package io.beatmaps.controllers

import io.beatmaps.api.MapDetail
import io.beatmaps.api.from
import io.beatmaps.cdnPrefix
import io.beatmaps.common.Config
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.common.dbo.joinVersions
import io.beatmaps.genericPage
import io.ktor.server.application.call
import io.ktor.server.locations.Location
import io.ktor.server.locations.get
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import kotlinx.html.meta
import org.jetbrains.exposed.sql.and
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

@Location("/search") class OldSearch

@Location("/browse") class OldBrowseController {
    @Location("/hot") data class Hot(val api: OldBrowseController)
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

    get<OldBrowseController.Hot> {
        call.respondRedirect("/")
    }

    get<OldSearch> {
        call.respondRedirect("/")
    }

    get<MapController.Detail> {
        genericPage(
            headerTemplate = {
                try {
                    transaction {
                        Beatmap.joinVersions().select {
                            Beatmap.id eq it.key.toInt(16) and Beatmap.deletedAt.isNull()
                        }.limit(1).complexToBeatmap().map { MapDetail.from(it, cdnPrefix()) }.firstOrNull()
                    }?.let {
                        meta("og:type", "website")
                        meta("og:site_name", "BeatSaver")
                        meta("og:title", it.name)
                        meta("og:url", "${Config.basename}/maps/${it.id}")
                        meta("og:image", it.publishedVersion()?.coverURL)
                        meta("og:description", it.description.take(400))
                    }
                } catch (_: NumberFormatException) {
                    // key isn't an int
                }
            }
        )
    }

    get<BeatmapController.RedirectOld> {
        try {
            transaction {
                Beatmap.select {
                    Beatmap.id eq it.key.toInt(16)
                }.limit(1).map { MapDetail.from(it, "") }.firstOrNull()
            }?.let {
                call.respondRedirect("/maps/${it.id}")
                true
            }
        } catch (_: NumberFormatException) {
            null
        } ?: run {
            call.respondRedirect("/")
        }
    }

    get<BeatsaverController.Detail> {
        genericPage()
    }
}
