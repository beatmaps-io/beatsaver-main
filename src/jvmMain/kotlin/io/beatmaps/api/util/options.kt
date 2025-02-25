package io.beatmaps.api.util

import de.nielsfalk.ktor.swagger.Metadata
import de.nielsfalk.ktor.swagger.get
import de.nielsfalk.ktor.swagger.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.resources.get
import io.ktor.server.resources.options
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext

inline fun <reified T : Any> Route.getWithOptions(
    noinline body: suspend RoutingContext.(T) -> Unit
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
    noinline body: suspend RoutingContext.(LOCATION) -> Unit
) {
    options<LOCATION> {
        call.response.header("Access-Control-Allow-Origin", "*")
        call.respond(HttpStatusCode.OK)
    }

    get<LOCATION>(metadata) {
        call.response.header("Access-Control-Allow-Origin", "*")
        body(it)
    }
}

inline fun <reified LOCATION : Any, reified ENTITY : Any> Route.postWithOptions(
    metadata: Metadata,
    noinline body: suspend RoutingContext.(LOCATION, ENTITY) -> Unit
) {
    options<LOCATION> {
        call.response.header("Access-Control-Allow-Origin", "*")
        call.respond(HttpStatusCode.OK)
    }

    post<LOCATION, ENTITY>(metadata) { loc, ent ->
        call.response.header("Access-Control-Allow-Origin", "*")
        body(loc, ent)
    }
}
