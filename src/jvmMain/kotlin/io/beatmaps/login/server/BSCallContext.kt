package io.beatmaps.login.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.util.toMap
import kotlinx.coroutines.runBlocking
import nl.myndocs.oauth2.json.JsonMapper
import nl.myndocs.oauth2.request.CallContext
import java.net.URI

class BSCallContext(private val applicationCall: ApplicationCall) : CallContext {
    override val path: String = applicationCall.request.path()
    override val method: String = applicationCall.request.httpMethod.value
    override val headers: Map<String, String> = applicationCall.request
        .headers
        .toMap()
        .mapValues { applicationCall.request.header(it.key) }
        .filterValues { it != null }
        .mapValues { it.value!! }

    override val queryParameters: Map<String, String> = applicationCall.request
        .queryParameters
        .toMap()
        .filterValues { it.isNotEmpty() }
        .mapValues { it.value.first() }

    override val formParameters by lazy {
        runBlocking {
            applicationCall.receiveParameters()
                .toMap()
                .filterValues { it.isNotEmpty() }
                .mapValues { it.value.first() }
        }
    }

    override fun respondStatus(statusCode: Int) {
        applicationCall.response.status(HttpStatusCode.fromValue(statusCode))
    }

    override fun respondHeader(name: String, value: String) {
        applicationCall.response.header(name, value)
    }

    override fun respondJson(content: Any) {
        runBlocking {
            applicationCall.respondText(
                JsonMapper.toJson(content),
                ContentType.Application.Json
            )
        }
    }

    override fun redirect(uri: String) {
        runBlocking {
            respondHeader("Refresh", "0;url=$uri")
            applicationCall.respondText("You are being redirected back to ${URI(uri).authority}")
        }
    }
}
