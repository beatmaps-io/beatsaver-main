package io.beatmaps.controllers

import io.beatmaps.api.PlaylistFull
import io.beatmaps.api.from
import io.beatmaps.common.Config
import io.beatmaps.common.dbo.Playlist
import io.beatmaps.genericPage
import io.beatmaps.login.Session
import io.beatmaps.util.cdnPrefix
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.locations.Location
import io.ktor.server.locations.get
import io.ktor.server.routing.Route
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlinx.html.link
import kotlinx.html.meta
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

@Location("/playlists")
class PlaylistController {
    @Location("/{id}/edit")
    data class Edit(val id: Int, val api: PlaylistController)

    @Location("/new")
    data class New(val api: PlaylistController)

    @Location("/{id}")
    data class Detail(val id: Int, val api: PlaylistController)
}

fun Route.playlistController() {
    get<PlaylistController> {
        genericPage {
            link("${Config.siteBase()}/playlists", "canonical")
        }
    }

    get<PlaylistController.New> {
        genericPage()
    }

    get<PlaylistController.Detail> { req ->
        val sess = call.sessions.get<Session>()
        val isAdmin = sess?.isAdmin() == true

        val playlistData = transaction {
            Playlist
                .selectAll()
                .where {
                    (Playlist.id eq req.id).let {
                        if (isAdmin) {
                            it
                        } else {
                            it and Playlist.deletedAt.isNull()
                        }
                    }
                }
                .limit(1)
                .firstOrNull()
                ?.let { PlaylistFull.from(it, cdnPrefix()) }
        }

        val validPlaylist = playlistData != null && (playlistData.type.anonymousAllowed || playlistData.owner.id == sess?.userId || isAdmin)

        genericPage(if (validPlaylist) HttpStatusCode.OK else HttpStatusCode.NotFound) {
            (if (validPlaylist) playlistData else null)?.let {
                meta("og:type", "website")
                meta("og:site_name", "BeatSaver")
                meta("og:title", it.name)
                meta("og:url", it.link(true))
                link(it.link(true), "canonical")
                meta("og:image", it.playlistImage)
                meta("og:description", it.description.take(400))
                meta("og:author", it.owner.name)
                meta("og:author:url", it.owner.profileLink(absolute = true))
            }
        }
    }

    get<PlaylistController.Edit> {
        genericPage()
    }
}
