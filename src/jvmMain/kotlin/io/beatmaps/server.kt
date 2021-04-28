package io.beatmaps

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
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
import io.beatmaps.common.*
import io.beatmaps.common.beatsaber.BSDifficulty
import io.beatmaps.common.db.setupDB
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
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.*
import io.ktor.html.respondHtmlTemplate
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.EntityTagVersion
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.jackson.jackson
import io.ktor.locations.Locations
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import io.ktor.util.*
import io.ktor.util.pipeline.PipelineContext
import kotlinx.datetime.*
import kotlinx.html.HEAD
import org.valiktor.ConstraintViolationException
import org.valiktor.i18n.mapToMessage
import pl.jutupe.ktor_rabbitmq.RabbitMQ
import java.math.BigInteger
import java.security.MessageDigest
import java.time.format.DateTimeParseException

suspend fun PipelineContext<*, io.ktor.application.ApplicationCall>.genericPage(statusCode: HttpStatusCode = HttpStatusCode.OK, headerTemplate: (HEAD.() -> Unit)? = null) {
    val sess = call.sessions.get<Session>()
    call.respondHtmlTemplate(MainTemplate(sess, GenericPageTemplate(sess)), statusCode) {
        headElements {
            headerTemplate?.invoke(this)
        }
        pageTitle = "BeatMaps.io"
    }
}

fun main() {
    setupLogging()
    setupDB()
    downloadsThread()

    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::beatmapsio).start(wait = true)
}

fun Application.beatmapsio() {
    installMetrics()

    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
            registerModule(JavaTimeModule())
            registerModule(KotlinTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            setSerializationInclusion(JsonInclude.Include.NON_NULL)
        }
    }

    install(SwaggerSupport) {
        path = "api/docs"
        swagger = Swagger().apply {
            info = Information(
                version = "0.1",
                title = "BeatMaps.io API",
                description = "WIP",
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
            genericPage(HttpStatusCode.NotFound)
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
            call.respond(HttpStatusCode.InternalServerError, cause.message ?: "")
        }

        exception<Throwable> { cause ->
            call.respond(HttpStatusCode.InternalServerError, "Internal Server Error")
            throw cause
        }
    }

    installSessions()
    installDiscordOauth()
    val mq = install(RabbitMQ) {
        setupAMQP()
    }

    routing {
        get("/") {
            genericPage()
        }

        cdnRoute()

        authRoute()
        mapDetailRoute(mq)
        userRoute()
        searchRoute()
        scoresRoute()
        testplayRoute()
        voteRoute()

        mapController()
        uploadController()
        policyController()

        static("/static") {
            resources()
        }
    }
}