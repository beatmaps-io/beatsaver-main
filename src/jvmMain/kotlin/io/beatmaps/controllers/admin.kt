package io.beatmaps.controllers

import io.beatmaps.genericPage
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.routing.Route

@Location("/modlog") class ModLog

fun Route.adminController() {
    get<ModLog> {
        genericPage()
    }
}
