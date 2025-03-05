package io.beatmaps.controllers

import de.nielsfalk.ktor.swagger.Ignore
import io.beatmaps.api.MapDetail
import io.beatmaps.api.from
import io.beatmaps.common.Config
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.common.dbo.joinCollaborators
import io.beatmaps.common.dbo.joinUploader
import io.beatmaps.common.dbo.joinVersions
import io.beatmaps.emptyPage
import io.beatmaps.genericPage
import io.beatmaps.login.Session
import io.beatmaps.previewBaseUrl
import io.beatmaps.util.cdnPrefix
import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.resources.get
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.html.link
import kotlinx.html.meta
import kotlinx.io.bytestring.encodeToByteString
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.lang.Integer.toHexString
import java.net.URLEncoder
import kotlin.time.Duration.Companion.seconds

@Resource("/maps")
class MapController {
    @Resource("/{key}")
    data class Detail(
        val key: String,
        @Ignore
        val api: MapController
    )

    @Resource("/viewer/{hash}")
    data class Viewer(
        val hash: String,
        @Ignore
        val api: MapController
    )

    @Resource("/{key}/embed")
    data class Embed(
        val key: String,
        @Ignore
        val api: MapController
    )
}

@Resource("/beatsaver")
class BeatsaverController {
    @Resource("/{key}")
    data class Detail(
        val key: String,
        @Ignore
        val api: BeatsaverController
    )
}

@Resource("/beatmap")
class BeatmapController {
    @Resource("/{key}")
    data class RedirectOld(
        val key: String,
        @Ignore
        val api: BeatmapController
    )
}

@Resource("/search")
class OldSearch

@Resource("/browse")
class OldBrowseController {
    @Resource("/hot")
    data class Hot(
        @Ignore
        val api: OldBrowseController
    )
}

@Resource("/mappers")
class Mappers

@Resource("/test")
class Testplays

fun Route.mapController() {
    get<Mappers> {
        genericPage {
            link("${Config.siteBase()}/mappers", "canonical")
        }
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

    get<MapController.Detail> { req ->
        val sess = call.sessions.get<Session>()
        val isAdmin = sess?.isAdmin() == true

        val mapData = try {
            transaction {
                Beatmap
                    .joinUploader()
                    .joinCollaborators()
                    .joinVersions(state = null)
                    .selectAll()
                    .where {
                        (Beatmap.id eq req.key.toInt(16)).let {
                            if (isAdmin) {
                                it
                            } else {
                                it and Beatmap.deletedAt.isNull()
                            }
                        }
                    }
                    .complexToBeatmap()
                    .firstOrNull()
                    ?.let { MapDetail.from(it, cdnPrefix()) }
            }
        } catch (_: NumberFormatException) {
            // key isn't an int
            null
        }

        val validMap = mapData != null && (mapData.publishedVersion() != null || mapData.uploader.id == sess?.userId || sess?.testplay == true || isAdmin)

        genericPage(if (validMap) HttpStatusCode.OK else HttpStatusCode.NotFound) {
            (if (validMap) mapData else null)?.let {
                val cleanDescription = it.description
                    .replace(Regex("[\\p{C}\\p{So}\uFE00-\uFE0F\\x{E0100}-\\x{E01EF}]+"), " ")
                    .replace(Regex(" {2,}"), " ")

                meta("og:type", "website")
                meta("og:site_name", "BeatSaver")
                meta("og:title", it.name)
                meta("og:url", it.link(true))
                link(it.link(true), "canonical")
                meta("og:image", it.publishedVersion()?.coverURL)
                meta("og:description", cleanDescription.take(400))

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
        }
    }

    get<MapController.Embed> {
        emptyPage()
    }

    get<BeatmapController.RedirectOld> {
        try {
            transaction {
                Beatmap.selectAll().where {
                    Beatmap.id eq it.key.toInt(16)
                }.limit(1).map { MapDetail.from(it, "") }.firstOrNull()
            }?.let {
                call.respondRedirect(it.link())
                true
            }
        } catch (_: NumberFormatException) {
            null
        } ?: run {
            call.respondRedirect("/")
        }
    }

    get<BeatsaverController.Detail> {
        genericPage {
            link("${Config.siteBase()}/maps/${it.key}", "canonical")
        }
    }
}
