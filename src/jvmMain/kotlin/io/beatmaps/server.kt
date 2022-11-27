package io.beatmaps

import com.rabbitmq.client.BuiltinExchangeType
import de.nielsfalk.ktor.swagger.SwaggerSupport
import de.nielsfalk.ktor.swagger.version.shared.Contact
import de.nielsfalk.ktor.swagger.version.shared.Information
import de.nielsfalk.ktor.swagger.version.v2.Swagger
import io.beatmaps.api.FailedUploadResponse
import io.beatmaps.api.alertsRoute
import io.beatmaps.api.mapDetailRoute
import io.beatmaps.api.modLogRoute
import io.beatmaps.api.playlistRoute
import io.beatmaps.api.reviewRoute
import io.beatmaps.api.scores.ScoreSaberServerException
import io.beatmaps.api.scoresRoute
import io.beatmaps.api.searchRoute
import io.beatmaps.api.testplayRoute
import io.beatmaps.api.userRoute
import io.beatmaps.api.voteRoute
import io.beatmaps.cloudflare.filenameUpdater
import io.beatmaps.common.StatusPagesCustom
import io.beatmaps.common.db.setupDB
import io.beatmaps.common.emailQueue
import io.beatmaps.common.genericQueueConfig
import io.beatmaps.common.installMetrics
import io.beatmaps.common.jackson
import io.beatmaps.common.json
import io.beatmaps.common.rabbitHost
import io.beatmaps.common.setupAMQP
import io.beatmaps.common.setupLogging
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
import io.beatmaps.login.installDiscordOauth
import io.beatmaps.login.installOauth2
import io.beatmaps.login.installSessions
import io.beatmaps.pages.GenericPageTemplate
import io.beatmaps.pages.templates.MainTemplate
import io.beatmaps.util.downloadsThread
import io.beatmaps.util.playlistStats
import io.beatmaps.util.reviewListeners
import io.beatmaps.util.scheduleTask
import io.beatmaps.websockets.mapUpdateEnricher
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.EntityTagVersion
import io.ktor.serialization.ContentConverter
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.html.respondHtmlTemplate
import io.ktor.server.http.content.defaultResource
import io.ktor.server.http.content.resources
import io.ktor.server.http.content.static
import io.ktor.server.locations.Locations
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.ParameterConversionException
import io.ktor.server.plugins.conditionalheaders.ConditionalHeaders
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.dataconversion.DataConversion
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.util.converters.DataConversionException
import io.ktor.util.pipeline.PipelineContext
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charset
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.html.HEAD
import org.flywaydb.core.Flyway
import org.valiktor.ConstraintViolationException
import org.valiktor.i18n.mapToMessage
import pl.jutupe.ktor_rabbitmq.RabbitMQ
import java.io.File
import java.math.BigInteger
import java.security.MessageDigest
import java.util.logging.Logger
import kotlin.time.Duration.Companion.nanoseconds

suspend fun PipelineContext<*, ApplicationCall>.genericPage(statusCode: HttpStatusCode = HttpStatusCode.OK, headerTemplate: (HEAD.() -> Unit)? = null) =
    call.genericPage(statusCode, headerTemplate)

suspend fun ApplicationCall.genericPage(statusCode: HttpStatusCode = HttpStatusCode.OK, headerTemplate: (HEAD.() -> Unit)? = null) {
    val sess = sessions.get<Session>()
    if (sess != null && sess.uniqueName == null && request.path() != "/username") {
        respondRedirect("/username")
    } else {
        respondHtmlTemplate(MainTemplate(sess, GenericPageTemplate(sess)), statusCode) {
            headElements {
                headerTemplate?.invoke(this)
            }
            pageTitle = "BeatSaver.com"
        }
    }
}

val migrationType = System.getenv("DISABLE_TEST_MIGRATIONS") != null

fun main() {
    setupLogging()
    setupDB(app = "BeatSaver Main").let { ds ->
        Flyway.configure()
            .dataSource(ds)
            .locations(if (migrationType) "db/migration" else "db")
            .load()
            .migrate()
    }

    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::beatmapsio).start(wait = true)
}

data class ErrorResponse(val error: String)

fun Application.beatmapsio() {
    installMetrics()

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

                override suspend fun serialize(contentType: ContentType, charset: Charset, typeInfo: TypeInfo, value: Any) =
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
    }

    install(XForwardedHeaders)

    install(ConditionalHeaders) {
        val md = MessageDigest.getInstance("MD5")
        val dockerHash = File("/etc/hostname").let {
            if (it.exists()) {
                it.readText()
            } else {
                ""
            }
        }
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

    install(DataConversion) {
        convert {
            decode { values ->
                values.singleOrNull()?.let {
                    try {
                        Instant.parse(it)
                    } catch (e: IllegalArgumentException) {
                        LocalDate.parse(it).atStartOfDayIn(TimeZone.UTC)
                    }
                } ?: throw DataConversionException("Cannot convert $values to Instant")
            }
            encode {
                listOf(it.toString())
            }
        }
    }

    install(Locations)
    install(StatusPagesCustom) {
        val errorLogger = Logger.getLogger("bmio.error")

        status(HttpStatusCode.NotFound) {
            /*(call.attributes.allKeys.find { it.name == "SessionKey" } as? AttributeKey<Any>)?.let {
                call.attributes.remove(it)
            }*/
            val reqPath = call.request.path()
            if (reqPath.startsWith("/api")) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Not Found"))
            } else if (reqPath.startsWith("/cdn")) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                genericPage(HttpStatusCode.NotFound)
            }
        }

        exception<ConstraintViolationException> { e ->
            call.respond(
                HttpStatusCode.BadRequest,
                FailedUploadResponse(
                    e.constraintViolations
                        .mapToMessage("messages")
                        .map { "${it.property}: ${it.message}" }
                )
            )
        }

        exception<UploadException> { cause ->
            call.respond(HttpStatusCode.BadRequest, cause.toResponse())
        }

        exception<ScoreSaberServerException> { cause ->
            call.respond(HttpStatusCode.BadGateway, ErrorResponse("Upstream responded with ${cause.originalException.response}"))
        }

        exception<DataConversionException> { cause ->
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(cause.message ?: ""))
        }

        exception<ParameterConversionException> { cause ->
            if (cause.type == Instant::class.toString()) {
                val now = Clock.System.now().let {
                    it.minus(it.nanosecondsOfSecond.nanoseconds)
                }
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("${cause.message}. Most likely you're missing a timezone. Example: $now"))
            } else {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: ""))
                throw cause
            }
        }

        exception<NotFoundException> {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("Not Found"))
        }

        exception<Throwable> { cause ->
            call.respond(HttpStatusCode.InternalServerError, "Internal Server Error")
            errorLogger.severe(cause.message)
        }
    }

    installSessions()
    installDiscordOauth()
    if (rabbitHost.isNotEmpty()) {
        install(RabbitMQ) {
            setupAMQP {
                exchangeDeclare("beatmaps.dlq", BuiltinExchangeType.TOPIC, true)
                exchangeDeclare("beatmaps", BuiltinExchangeType.TOPIC, true)
                queueDeclare("vote", true, false, false, genericQueueConfig)
                queueBind("vote", "beatmaps", "vote.#")

                queueDeclare("uvstats", true, false, false, genericQueueConfig)
                queueBind("uvstats", "beatmaps", "user.stats.*")

                queueDeclare("bm.updateStream", true, false, false, genericQueueConfig)
                queueBind("bm.updateStream", "beatmaps", "maps.*.updated")
                queueBind("bm.updateStream", "beatmaps", "maps.*.updated.*")

                queueDeclare("bm.mapPlaylistTrigger", true, false, false, genericQueueConfig)
                queueBind("bm.mapPlaylistTrigger", "beatmaps", "maps.*.updated.deleted")
                queueBind("bm.mapPlaylistTrigger", "beatmaps", "maps.*.updated.state")

                queueDeclare("bm.playlistStats", true, false, false, genericQueueConfig)
                queueBind("bm.playlistStats", "beatmaps", "playlists.*.updated")
                queueBind("bm.playlistStats", "beatmaps", "playlists.*.stats")

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
            }
        }
        downloadsThread()
        filenameUpdater()
        reviewListeners()
    }
    installOauth2()

    scheduleTask()
    playlistStats()
    emailQueue()

    routing {
        get("/") {
            genericPage()
        }

        cdnRoute()

        authRoute()
        mapDetailRoute()
        userRoute()
        searchRoute()
        scoresRoute()
        testplayRoute()
        voteRoute()
        playlistRoute()
        alertsRoute()
        modLogRoute()
        reviewRoute()

        mapController()
        userController()
        playlistController()
        adminController()
        uploadController()
        policyController()

        mapUpdateEnricher()

        static("static") {
            resources()
            defaultResource("404.html")
        }
    }
}
