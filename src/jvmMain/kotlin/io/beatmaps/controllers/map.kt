package io.beatmaps.controllers

import io.beatmaps.api.MapDetail
import io.beatmaps.api.from
import io.beatmaps.common.Config
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.common.dbo.joinCollaborators
import io.beatmaps.common.dbo.joinVersions
import io.beatmaps.genericPage
import io.beatmaps.login.Session
import io.beatmaps.previewBaseUrl
import io.beatmaps.util.cdnPrefix
import io.ktor.server.application.call
import io.ktor.server.locations.Location
import io.ktor.server.locations.get
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.html.meta
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.lang.Integer.toHexString
import java.net.URLEncoder
import kotlin.time.Duration.Companion.seconds

@Location("/maps")
class MapController {
    @Location("/{key}")
    data class Detail(val key: String, val api: MapController)

    @Location("/viewer/{hash}")
    data class Viewer(val hash: String, val api: MapController)
}

@Location("/beatsaver")
class BeatsaverController {
    @Location("/{key}")
    data class Detail(val key: String, val api: BeatsaverController)
}

@Location("/beatmap")
class BeatmapController {
    @Location("/{key}")
    data class RedirectOld(val key: String, val api: BeatmapController)
}

@Location("/search")
class OldSearch

@Location("/browse")
class OldBrowseController {
    @Location("/hot")
    data class Hot(val api: OldBrowseController)
}

@Location("/mappers")
class Mappers

@Location("/test")
class Testplays

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

    get<MapController.Viewer> {
        val sess = call.sessions.get<Session>()

        transaction {
            Beatmap
                .join(Versions, JoinType.INNER, onColumn = Beatmap.id, otherColumn = Versions.mapId)
                .selectAll()
                .where {
                    (Versions.hash eq it.hash) and Beatmap.deletedAt.isNull()
                }
                .complexToBeatmap()
                .firstOrNull()
        }?.let { map ->
            val url = previewBaseUrl + if (map.versions.any { v -> v.value.state == EMapState.Published }) {
                "?id=${toHexString(map.id.value)}"
            } else if (map.uploaderId.value == sess?.userId) {
                val exp = Clock.System.now().plus(60.seconds).epochSeconds
                val mapUrl = "${Config.cdnBase("", true)}/${it.hash}.zip?${CdnSig.queryParams(it.hash, exp)}&.zip"

                "?noProxy=true&url=" + withContext(Dispatchers.IO) {
                    URLEncoder.encode(mapUrl, "UTF-8")
                }
            } else {
                ""
            }

            call.respondRedirect(url)
        }
    }

    get<MapController.Detail> {
        genericPage(
            headerTemplate = {
                try {
                    transaction {
                        Beatmap
                            .joinCollaborators()
                            .joinVersions()
                            .selectAll()
                            .where {
                                Beatmap.id eq it.key.toInt(16) and Beatmap.deletedAt.isNull()
                            }.limit(1).complexToBeatmap().map { MapDetail.from(it, cdnPrefix()) }.firstOrNull()
                    }?.let {
                        meta("og:type", "website")
                        meta("og:site_name", "BeatSaver")
                        meta("og:title", it.name)
                        meta("og:url", "${Config.siteBase()}/maps/${it.id}")
                        meta("og:image", it.publishedVersion()?.coverURL)
                        meta("og:description", it.description.take(400))

                        // Joining mappers together for the og:author field so that:
                        // 1. Uploader will be first
                        // 2. All non-last collaborators will be joined with ", "
                        // 3. The last collaborator will be joined with " and "
                        val authors = (listOf(it.uploader) + it.collaborators.orEmpty()).map { u -> u.name }
                        val authorString = authors.reduceIndexed { index, acc, s -> "$acc${if (index == authors.lastIndex) " and" else ","} $s" }
                        meta("og:author", authorString)

                        // There can only be one URL so we only add it when there is no collaborator
                        // Otherwise, it may be confusing
                        if (it.collaborators.isNullOrEmpty()) {
                            meta("og:author:url", "${Config.siteBase()}/profile/${it.uploader.id}")
                        }
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
                Beatmap.selectAll().where {
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
