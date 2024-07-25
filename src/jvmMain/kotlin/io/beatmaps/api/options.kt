package io.beatmaps.api

import de.nielsfalk.ktor.swagger.Metadata
import de.nielsfalk.ktor.swagger.swaggerUi
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.locations.get
import io.ktor.server.locations.options
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.util.pipeline.PipelineContext

inline fun <reified T : Any> Route.getWithOptions(
    noinline body: suspend PipelineContext<Unit, ApplicationCall>.(T) -> Unit
) {
    options<T> {
        call.response.header("Access-Control-Allow-Origin", "*")
        call.respond(HttpStatusCode.OK)
    }

    get<T> {
        call.response.header("Access-Control-Allow-Origin", "*")
        body(it)
    }
}

inline fun <reified LOCATION : Any> Route.getWithOptions(
    metadata: Metadata,
    noinline body: suspend PipelineContext<Unit, ApplicationCall>.(LOCATION) -> Unit
): Route {
    application.swaggerUi.apply {
        metadata.apply<LOCATION, Unit>(HttpMethod.Get)
    }
    return get(body)
}
