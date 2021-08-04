package io.beatmaps.controllers

import io.beatmaps.api.MapDetail
import io.beatmaps.api.from
import io.beatmaps.common.Config
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.genericPage
import io.ktor.application.call
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.response.respondRedirect
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

@Location("/beatmap") class BeatmapController {
    @Location("/{key}") data class RedirectOld(val key: String, val api: BeatmapController)
}

@Location("/profile") class UserController {
    @Location("/{id?}") data class Detail(val id: Int? = null, val api: UserController)
}

@Location("/mappers") class Mappers
@Location("/test") class Testplays

fun Route.mapController() {
    get<Mappers> {
        genericPage()
    }

    get<Testplays> {
        genericPage()
    }

    get<MapController.Detail> {
        genericPage(headerTemplate = {
            try {
                transaction {
                    Beatmap.joinVersions().select {
                        Beatmap.id eq it.key.toInt(16)
                    }.limit(1).complexToBeatmap().map { MapDetail.from(it) }.firstOrNull()
                }?.let {
                    meta("og:type", "website")
                    meta("og:site_name", "BeatSaver")
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

    get<BeatmapController.RedirectOld> {
        transaction {
            Beatmap.joinVersions().select {
                Versions.key64 eq it.key
            }.limit(1).complexToBeatmap().map { MapDetail.from(it) }.firstOrNull()
        }?.let {
            call.respondRedirect("/maps/${it.id}")
        } ?: run {
            call.respondRedirect("/")
        }
    }

    get<BeatsaverController.Detail> {
        genericPage()
    }

    get<UserController.Detail> {
        genericPage()
    }
}
