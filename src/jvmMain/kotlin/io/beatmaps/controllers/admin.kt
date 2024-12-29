package io.beatmaps.controllers

import io.beatmaps.genericPage
import io.ktor.server.locations.Location
import io.ktor.server.locations.get
import io.ktor.server.routing.Route

@Location("/modlog")
class ModLog

@Location("/modreview")
class ModReview

@Location("/issues")
class IssuesController {
    @Location("/{id}")
    data class Detail(val id: Int, val api: IssuesController)
}

fun Route.adminController() {
    get<ModLog> {
        genericPage()
    }

    get<ModReview> {
        genericPage()
    }

    get<IssuesController> {
        genericPage()
    }

    get<IssuesController.Detail> {
        // We could include extra detail but this page is meant to be private
        genericPage()
    }
}
