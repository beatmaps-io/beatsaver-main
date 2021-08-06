package io.beatmaps

import com.toxicbakery.bcrypt.Bcrypt
import de.nielsfalk.ktor.swagger.SwaggerSupport
import de.nielsfalk.ktor.swagger.version.shared.Contact
import de.nielsfalk.ktor.swagger.version.shared.Information
import de.nielsfalk.ktor.swagger.version.v2.Swagger
import io.beatmaps.api.FailedUploadResponse
import io.beatmaps.api.mapDetailRoute
import io.beatmaps.api.scoresRoute
import io.beatmaps.api.searchRoute
import io.beatmaps.api.testplayRoute
import io.beatmaps.api.userRoute
import io.beatmaps.api.voteRoute
import io.beatmaps.common.db.setupDB
import io.beatmaps.common.jackson
import io.beatmaps.common.json
import io.beatmaps.common.rabbitHost
import io.beatmaps.common.setupAMQP
import io.beatmaps.common.setupLogging
import io.beatmaps.controllers.UploadException
import io.beatmaps.controllers.cdnRoute
import io.beatmaps.controllers.mapController
import io.beatmaps.controllers.policyController
import io.beatmaps.controllers.uploadController
import io.beatmaps.login.Session
import io.beatmaps.login.authRoute
import io.beatmaps.login.installDiscordOauth
import io.beatmaps.login.installSessions
import io.beatmaps.pages.GenericPageTemplate
import io.beatmaps.pages.templates.MainTemplate
import io.beatmaps.websockets.mapsWebsocket
import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ConditionalHeaders
import io.ktor.features.ContentConverter
import io.ktor.features.ContentNegotiation
import io.ktor.features.DataConversion
import io.ktor.features.ParameterConversionException
import io.ktor.features.StatusPages
import io.ktor.features.XForwardedHeaderSupport
import io.ktor.html.respondHtmlTemplate
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.cio.websocket.pingPeriod
import io.ktor.http.content.EntityTagVersion
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.jackson.JacksonConverter
import io.ktor.locations.Locations
import io.ktor.request.ApplicationReceiveRequest
import io.ktor.request.path
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.serialization.SerializationConverter
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import io.ktor.util.DataConversionException
import io.ktor.util.pipeline.PipelineContext
import io.ktor.websocket.WebSockets
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.html.HEAD
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.StringFormat
import org.valiktor.ConstraintViolationException
import org.valiktor.i18n.mapToMessage
import pl.jutupe.ktor_rabbitmq.RabbitMQ
import java.math.BigInteger
import java.security.MessageDigest
import java.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

suspend fun PipelineContext<*, ApplicationCall>.genericPage(statusCode: HttpStatusCode = HttpStatusCode.OK, headerTemplate: (HEAD.() -> Unit)? = null) {
    val sess = call.sessions.get<Session>()
    call.respondHtmlTemplate(MainTemplate(sess, GenericPageTemplate(sess)), statusCode) {
        headElements {
            headerTemplate?.invoke(this)
        }
        pageTitle = "BeatSaver.com"
    }
}

fun main() {
    setupLogging()
    setupDB()
    downloadsThread()

    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::beatmapsio).start(wait = true)
}

data class ErrorResponse(val error: String)

@ExperimentalSerializationApi
fun Application.beatmapsio() {
    installMetrics()

    install(ContentNegotiation) {
        val kotlinx = SerializationConverter(json as StringFormat)
        val jsConv = JacksonConverter(jackson)

        register(
            ContentType.Application.Json,
            object : ContentConverter {
                override suspend fun convertForReceive(context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>) =
                    try {
                        kotlinx.convertForReceive(context)
                    } catch (e: Exception) {
                        null
                    } ?: jsConv.convertForReceive(context)

                override suspend fun convertForSend(context: PipelineContext<Any, ApplicationCall>, contentType: ContentType, value: Any) =
                    try {
                        kotlinx.convertForSend(context, contentType, value)
                    } catch (e: Exception) {
                        null
                    } ?: jsConv.convertForSend(context, contentType, value)
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

    install(XForwardedHeaderSupport)

    install(ConditionalHeaders) {
        val md = MessageDigest.getInstance("MD5")
        val dockerHash = System.getenv("HOSTNAME") ?: ""
        md.update(dockerHash.toByteArray())

        val fx = "%0" + md.digestLength * 2 + "x"
        val etag = String.format(fx, BigInteger(1, md.digest()))

        version { outgoingContent ->
            when (outgoingContent.contentType?.withoutParameters()) {
                ContentType.Text.CSS -> listOf(EntityTagVersion(etag))
                ContentType.Text.JavaScript, ContentType.Application.JavaScript -> listOf(EntityTagVersion(etag))
                else -> emptyList()
            }
        }
    }

    install(DataConversion) {
        convert<Instant> {
            decode { values, _ ->
                values.singleOrNull()?.let {
                    try {
                        Instant.parse(it)
                    } catch (e: IllegalArgumentException) {
                        LocalDate.parse(it).atStartOfDayIn(TimeZone.UTC)
                    }
                }
            }
            encode {
                when (it) {
                    null -> listOf()
                    is Instant -> listOf(it.toString())
                    else -> throw DataConversionException("Cannot convert $it as Instant")
                }
            }
        }
    }

    install(Locations)
    install(StatusPages) {
        status(HttpStatusCode.NotFound) {
            /*(call.attributes.allKeys.find { it.name == "SessionKey" } as? AttributeKey<Any>)?.let {
                call.attributes.remove(it)
            }*/
            val apiRoute = call.request.path().startsWith("/api")
            if (apiRoute) {
                call.respond(HttpStatusCode.NotFound, "Not Found")
            } else {
                genericPage(HttpStatusCode.NotFound)
            }
        }

        exception<ConstraintViolationException> { e ->
            call.respond(HttpStatusCode.BadRequest,
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

        exception<DataConversionException> { cause ->
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(cause.message ?: ""))
        }

        exception<ParameterConversionException> { cause ->
            if (cause.type == Instant::class.toString()) {
                val now = Clock.System.now().let {
                    it.minus(nanoseconds(it.nanosecondsOfSecond))
                }
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("${cause.message}. Most likely you're missing a timezone. Example: $now"))
            } else {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: ""))
                throw cause
            }
        }

        exception<Throwable> { cause ->
            call.respond(HttpStatusCode.InternalServerError, "Internal Server Error")
            throw cause
        }
    }

    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(60)
    }

    installSessions()
    installDiscordOauth()
    if (rabbitHost.isNotEmpty()) {
        install(RabbitMQ) {
            setupAMQP()
        }
    }

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

        mapController()
        uploadController()
        policyController()

        mapsWebsocket()

        static("/static") {
            resources()
        }
    }
}