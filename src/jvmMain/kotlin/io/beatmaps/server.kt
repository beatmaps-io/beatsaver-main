package io.beatmaps

import com.rabbitmq.client.BuiltinExchangeType
import de.nielsfalk.ktor.swagger.SwaggerSupport
import de.nielsfalk.ktor.swagger.version.shared.Contact
import de.nielsfalk.ktor.swagger.version.shared.Information
import de.nielsfalk.ktor.swagger.version.v2.Swagger
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.ApiException
import io.beatmaps.api.ServerApiException
import io.beatmaps.api.UploadResponse
import io.beatmaps.api.UploadValidationInfo
import io.beatmaps.api.UserApiException
import io.beatmaps.api.alertsRoute
import io.beatmaps.api.bookmarkRoute
import io.beatmaps.api.collaborationRoute
import io.beatmaps.api.issueRoute
import io.beatmaps.api.mapDetailRoute
import io.beatmaps.api.modLogRoute
import io.beatmaps.api.playlistRoute
import io.beatmaps.api.questRoute
import io.beatmaps.api.reviewRoute
import io.beatmaps.api.scores.ScoreSaberServerException
import io.beatmaps.api.scoresRoute
import io.beatmaps.api.search.SolrImporter.solrUpdater
import io.beatmaps.api.searchRoute
import io.beatmaps.api.testplayRoute
import io.beatmaps.api.userRoute
import io.beatmaps.api.voteRoute
import io.beatmaps.cloudflare.CaptchaVerifier
import io.beatmaps.cloudflare.filenameUpdater
import io.beatmaps.common.Config
import io.beatmaps.common.StatusPagesCustom
import io.beatmaps.common.amqp.emailQueue
import io.beatmaps.common.amqp.genericQueueConfig
import io.beatmaps.common.amqp.rabbitHost
import io.beatmaps.common.amqp.setupAMQP
import io.beatmaps.common.beatsaber.BMConstraintViolation
import io.beatmaps.common.beatsaber.BMConstraintViolationMessage
import io.beatmaps.common.beatsaber.BMPropertyInfo
import io.beatmaps.common.db.setupDB
import io.beatmaps.common.installMetrics
import io.beatmaps.common.jackson
import io.beatmaps.common.json
import io.beatmaps.common.jsonClient
import io.beatmaps.common.setupLogging
import io.beatmaps.common.util.BadParameter
import io.beatmaps.controllers.UploadException
import io.beatmaps.controllers.adminController
import io.beatmaps.controllers.cdnRoute
import io.beatmaps.controllers.mapController
import io.beatmaps.controllers.playlistController
import io.beatmaps.controllers.policyController
import io.beatmaps.controllers.uploadController
import io.beatmaps.controllers.userController
import io.beatmaps.login.Session
import io.beatmaps.login.authRoute
import io.beatmaps.login.discordLogin
import io.beatmaps.login.installOauth
import io.beatmaps.login.installSessions
import io.beatmaps.login.patreon.patreonLink
import io.beatmaps.login.server.TokenStoreCleaner.Companion.scheduleTokenCleanup
import io.beatmaps.login.server.installOauth2
import io.beatmaps.pages.GenericPageTemplate
import io.beatmaps.pages.templates.MainTemplate
import io.beatmaps.util.BeatsageCleanse.Companion.scheduleCleanser
import io.beatmaps.util.CheckScheduled.Companion.scheduleTask
import io.beatmaps.util.alertsThread
import io.beatmaps.util.downloadsThread
import io.beatmaps.util.playlistStats
import io.beatmaps.util.reviewListeners
import io.beatmaps.websockets.mapUpdateEnricher
import io.ktor.client.HttpClient
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.EntityTagVersion
import io.ktor.serialization.ContentConverter
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.html.respondHtmlTemplate
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.conditionalheaders.ConditionalHeaders
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.request.path
import io.ktor.server.request.uri
import io.ktor.server.resources.Resources
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ClosedByteChannelException
import io.ktor.utils.io.charsets.Charset
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.html.HEAD
import kotlinx.html.link
import nl.myndocs.oauth2.exception.InvalidGrantException
import nl.myndocs.oauth2.exception.OauthException
import nl.myndocs.oauth2.exception.toMap
import nl.myndocs.oauth2.tokenstore.inmemory.InMemoryDeviceCodeStore
import org.flywaydb.core.Flyway
import org.valiktor.ConstraintViolationException
import org.valiktor.i18n.toMessage
import pl.jutupe.ktor_rabbitmq.RabbitMQ
import java.io.File
import java.math.BigInteger
import java.nio.charset.MalformedInputException
import java.security.MessageDigest
import java.util.logging.Level
import java.util.logging.Logger
import javax.sql.DataSource
import kotlin.time.Duration.Companion.nanoseconds

suspend fun RoutingContext.genericPage(statusCode: HttpStatusCode = HttpStatusCode.OK, headerTemplate: (HEAD.() -> Unit)? = null) =
    call.genericPage(statusCode, headerTemplate)

fun ApplicationCall.getNonce() =
    request.headers["X-CSP-Nonce"]

val dockerHash = File("/etc/hostname").let {
    if (it.exists()) {
        it.readText().trim()
    } else {
        ""
    }
}

suspend fun ApplicationCall.genericPage(statusCode: HttpStatusCode = HttpStatusCode.OK, headerTemplate: (HEAD.() -> Unit)? = null, includeHeader: Boolean = true) {
    val sess = sessions.get<Session>()
    val nonce = getNonce()
    val provider = CaptchaVerifier.provider(this)

    // Force renew session
    if (statusCode == HttpStatusCode.OK && sess != null) {
        sessions.set(sess)
    }

    if (sess != null && sess.uniqueName == null && request.path() != "/username") {
        respondRedirect("/username")
    } else {
        respondHtmlTemplate(MainTemplate(sess, GenericPageTemplate(sess, provider, nonce), "BeatSaver.com", includeHeader, nonce), statusCode) {
            headElements {
                headerTemplate?.invoke(this)
            }
        }
    }
}

suspend fun RoutingContext.emptyPage(statusCode: HttpStatusCode = HttpStatusCode.OK, headerTemplate: (HEAD.() -> Unit)? = null) =
    call.genericPage(statusCode, headerTemplate, false)

enum class DbMigrationType(val folder: String) {
    None(""), Standard("db/migration"), Test("db");

    companion object {
        val fromEnv = when {
            System.getenv("DISABLE_MIGRATIONS") != null -> None
            System.getenv("DISABLE_TEST_MIGRATIONS") == null -> Test
            else -> Standard
        }
    }
}

fun main() {
    setupLogging()
    setupDB(app = "BeatSaver Main").let { ds ->
        val type = DbMigrationType.fromEnv
        if (type != DbMigrationType.None) migrateDB(ds, type)
    }

    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::beatmapsio).start(wait = true)
}

fun migrateDB(ds: DataSource, type: DbMigrationType) {
    Flyway.configure()
        .dataSource(ds)
        .locations(type.folder)
        .load()
        .migrate()
}

fun Application.beatmapsio(httpClient: HttpClient = jsonClient) {
    install(ContentNegotiation) {
        val kotlinx = KotlinxSerializationConverter(json)
        val jsConv = JacksonConverter(jackson)

        register(
            ContentType.Application.Json,
            object : ContentConverter {
                override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel) =
                    try {
                        kotlinx.deserialize(charset, typeInfo, content)
                    } catch (e: Exception) {
                        null
                    } ?: jsConv.deserialize(charset, typeInfo, content)

                override suspend fun serialize(contentType: ContentType, charset: Charset, typeInfo: TypeInfo, value: Any?) =
                    try {
                        kotlinx.serialize(contentType, charset, typeInfo, value)
                    } catch (e: Exception) {
                        null
                    } ?: jsConv.serialize(contentType, charset, typeInfo, value)
            }
        )
    }

    install(SwaggerSupport) {
        path = "api/docs"
        swagger = Swagger().apply {
            info = Information(
                version = "0.1",
                title = "BeatSaver API",
                description = """
                    WIP
                    
                    If you want to keep any kind of mirror instead of making 100s of requests instead consider subscribing to the websocket api.
                    wss://ws.beatsaver.com/maps
                    
                    Messages will be in the style {"type": "MAP_UPDATE", "msg": __MAP_DATA_HERE__}
                """.trimIndent(),
                contact = Contact(
                    name = "Top_Cat"
                )
            )
        }
        nonce = { call ->
            call.getNonce()
        }
    }

    install(XForwardedHeaders)

    install(ConditionalHeaders) {
        val md = MessageDigest.getInstance("MD5")
        md.update(dockerHash.toByteArray())

        val fx = "%0" + md.digestLength * 2 + "x"
        val etag = String.format(fx, BigInteger(1, md.digest()))

        version { _, outgoingContent ->
            when (outgoingContent.contentType?.withoutParameters()) {
                ContentType.Text.CSS -> listOf(EntityTagVersion(etag))
                ContentType.Text.JavaScript, ContentType.Application.JavaScript -> listOf(EntityTagVersion(etag))
                else -> emptyList()
            }
        }
    }

    install(Resources)
    install(StatusPagesCustom) {
        val errorLogger = Logger.getLogger("bmio.error")

        status(HttpStatusCode.NotFound) { call, code ->
            val reqPath = call.request.path()
            if (reqPath.startsWith("/api")) {
                call.respond(HttpStatusCode.NotFound, ActionResponse.error("Not Found"))
            } else if (reqPath.startsWith("/cdn")) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.genericPage(HttpStatusCode.NotFound)
            }
        }

        exception<ConstraintViolationException> { call, e ->
            call.respond(
                HttpStatusCode.BadRequest,
                UploadResponse(
                    e.constraintViolations
                        .take(1000)
                        .map {
                            when (it) {
                                is BMConstraintViolation -> it.toMessage("messages")
                                else -> it.toMessage("messages")
                            }
                        }
                        .map {
                            when (it) {
                                is BMConstraintViolationMessage -> UploadValidationInfo(it.propertyInfo, it.message)
                                else -> UploadValidationInfo(listOf(BMPropertyInfo(it.property)), it.message)
                            }
                        }
                )
            )
        }

        exception<UploadException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, cause.toResponse())
        }

        exception<ScoreSaberServerException> { call, cause ->
            call.respond(HttpStatusCode.BadGateway, ActionResponse.error("Upstream responded with ${cause.originalException.response}"))
        }

        exception<BadRequestException> { call, cause ->
            when (val c2 = cause.cause?.cause) {
                is BadParameter -> {
                    val helpMessage = if (c2.paramType == Instant::class) {
                        val now = Clock.System.now().let {
                            it.minus(it.nanosecondsOfSecond.nanoseconds)
                        }
                        ". Most likely you're missing a timezone. Example: $now"
                    } else {
                        ""
                    }
                    call.respond(HttpStatusCode.InternalServerError, ActionResponse.error("Failed to convert parameter '${c2.paramName}' to ${c2.paramType.simpleName}$helpMessage"))
                }
                else -> call.respond(HttpStatusCode.InternalServerError, ActionResponse.error("Bad request, check parameters"))
            }
        }

        exception<ApiException> { call, cause ->
            val code = when (cause) {
                is UserApiException -> HttpStatusCode.BadRequest
                is ServerApiException -> HttpStatusCode.InternalServerError
            }
            call.respond(code, cause.toResponse())
        }

        exception<NotFoundException> { call, _ ->
            call.respond(HttpStatusCode.NotFound, ActionResponse.error("Not Found"))
        }

        exception<InvalidGrantException> { call, _ ->
            call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
        }

        exception<OauthException> { call, it ->
            call.respond(HttpStatusCode.BadRequest, it.toMap())
        }

        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest)
        }

        exception<ClosedByteChannelException> { _, _ ->
            // Do nothing, client is gone
        }

        exception<MalformedInputException> { call, _ ->
            call.respond(HttpStatusCode.InternalServerError, "Internal Server Error")
            errorLogger.log(Level.SEVERE, "MalformedInputException during request `${call.request.uri}`")
        }

        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, "Internal Server Error")
            errorLogger.log(Level.SEVERE, "Error processing request", cause)
        }
    }

    installMetrics()
    installSessions()
    installOauth(httpClient)
    if (rabbitHost.isNotEmpty()) {
        install(RabbitMQ) {
            setupAMQP(false) {
                exchangeDeclare("beatmaps.dlq", BuiltinExchangeType.TOPIC, true)
                exchangeDeclare("beatmaps", BuiltinExchangeType.TOPIC, true)
                queueDeclare("vote", true, false, false, genericQueueConfig)
                queueBind("vote", "beatmaps", "vote.#")

                queueDeclare("uvstats", true, false, false, genericQueueConfig)
                queueBind("uvstats", "beatmaps", "user.stats.*")

                queueDeclare("maptouv", true, false, false, genericQueueConfig)
                queueBind("maptouv", "beatmaps", "maps.*.updated.deleted")
                queueBind("maptouv", "beatmaps", "maps.*.updated.state")

                queueDeclare("bm.updateStream", true, false, false, genericQueueConfig)
                queueBind("bm.updateStream", "beatmaps", "maps.*.updated")
                queueBind("bm.updateStream", "beatmaps", "maps.*.updated.*")

                queueDeclare("bm.mapPlaylistTrigger", true, false, false, genericQueueConfig)
                queueBind("bm.mapPlaylistTrigger", "beatmaps", "maps.*.updated.deleted") // Map deleted
                queueBind("bm.mapPlaylistTrigger", "beatmaps", "maps.*.updated.state") // Map publish / unpublish

                queueDeclare("bm.playlistStats", true, false, false, genericQueueConfig)
                queueBind("bm.playlistStats", "beatmaps", "playlists.*.updated") // New songs
                queueBind("bm.playlistStats", "beatmaps", "playlists.*.stats") // Stats trigger from map change

                queueDeclare("bm.downloadCount", true, false, false, genericQueueConfig)
                queueBind("bm.downloadCount", "beatmaps", "download.#")

                queueDeclare("cdn.r2", true, false, false, genericQueueConfig)
                queueBind("cdn.r2", "beatmaps", "cdn.#")

                queueDeclare("email", true, false, false, genericQueueConfig)
                queueBind("email", "beatmaps", "email")

                queueDeclare("bm.sentiment", true, false, false, genericQueueConfig)
                queueBind("bm.sentiment", "beatmaps", "reviews.*.created")
                queueBind("bm.sentiment", "beatmaps", "reviews.*.updated")
                queueBind("bm.sentiment", "beatmaps", "reviews.*.deleted")

                queueDeclare("bm.reviewDiscordHook", true, false, false, genericQueueConfig)
                queueBind("bm.reviewDiscordHook", "beatmaps", "reviews.*.created")

                queueDeclare("bm.issuesDiscordHook", true, false, false, genericQueueConfig)
                queueBind("bm.issuesDiscordHook", "beatmaps", "issues.*.created")

                queueDeclare("bm.alertCount", true, false, false, genericQueueConfig)
                queueBind("bm.alertCount", "beatmaps", "user.alerts.*")

                queueDeclare("bm.solr", true, false, false, genericQueueConfig)
                queueBind("bm.solr", "beatmaps", "maps.*.updated")
                queueBind("bm.solr", "beatmaps", "maps.*.updated.*")

                queueDeclare("bm.solr-user", true, false, false, genericQueueConfig)
                queueBind("bm.solr-user", "beatmaps", "user.*.updated.admin")

                queueDeclare("bm.solr-user-info", true, false, false, genericQueueConfig)
                queueBind("bm.solr-user-info", "beatmaps", "user.*.updated.*")
                queueBind("bm.solr-user-info", "beatmaps", "user.stats.*")

                queueDeclare("bm.solr-playlist", true, false, false, genericQueueConfig)
                queueBind("bm.solr-playlist", "beatmaps", "playlists.*.updated.*")
                queueBind("bm.solr-playlist", "beatmaps", "playlists.*.created")
            }
        }
        downloadsThread()
        alertsThread()
        filenameUpdater(httpClient)
        reviewListeners(httpClient)
        playlistStats()
        emailQueue()
    }
    val deviceCodeStore = InMemoryDeviceCodeStore()
    installOauth2(deviceCodeStore)

    scheduleTask()
    scheduleCleanser()
    scheduleTokenCleanup()
    solrUpdater()

    routing {
        get("/") {
            genericPage {
                link(Config.siteBase(), "canonical")
            }
        }

        cdnRoute()

        authRoute(httpClient)
        discordLogin(httpClient)
        patreonLink(httpClient)
        mapDetailRoute()
        userRoute(httpClient)
        searchRoute()
        scoresRoute()
        testplayRoute(httpClient)
        voteRoute(httpClient)
        playlistRoute(httpClient)
        alertsRoute()
        modLogRoute()
        reviewRoute(httpClient)
        bookmarkRoute()
        collaborationRoute()
        questRoute(deviceCodeStore)
        issueRoute(httpClient)

        mapController()
        userController()
        playlistController()
        adminController()
        uploadController(httpClient)
        policyController()

        mapUpdateEnricher()

        staticResources("/static", "assets")
    }
}
