package io.beatmaps.controllers

import io.beatmaps.genericPage
import io.ktor.server.locations.Location
import io.ktor.server.locations.get
import io.ktor.server.routing.Route

@Location("/modlog") class ModLog

fun Route.adminController() {
    get<ModLog> {
        genericPage()
    }
}
