package io.beatmaps.controllers

import io.beatmaps.common.Config
import io.beatmaps.api.MapDetail
import io.beatmaps.api.from
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.genericPage
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.routing.Route
import kotlinx.html.meta
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

@Location("/maps") class MapController {
    @Location("/{key}") data class Detail(val key: String, val api: MapController)
}

@Location("/beatsaver") class BeatsaverController {
    @Location("/{key}") data class Detail(val key: String, val api: BeatsaverController)
}

@Location("/profile") class UserController {
    @Location("/{id?}") data class Detail(val id: Int? = null, val api: UserController)
}

fun Route.mapController() {
    get<MapController.Detail> {
        genericPage(headerTemplate = {
            try {
                transaction {
                    Beatmap.joinVersions().select {
                        Beatmap.id eq it.key.toInt()
                    }.limit(1).complexToBeatmap().map { MapDetail.from(it) }.firstOrNull()
                }?.let {
                    meta("og:type", "website")
                    meta("og:site_name", "BeatMaps.io")
                    meta("og:title", it.name)
                    meta("og:url", "${Config.basename}/maps/${it.id}")
                    meta("og:description", it.description.take(400))
                    meta("og:image", "${Config.cdnbase}/${it.publishedVersion()?.hash}.jpg")
                }
            } catch (_: NumberFormatException) {
                // key isn't an int
            }
        })
    }

    get<BeatsaverController.Detail> {
        genericPage()
    }

    get<UserController.Detail> {
        genericPage()
    }
}
