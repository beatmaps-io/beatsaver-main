package io.beatmaps.controllers

import io.beatmaps.api.PlaylistFull
import io.beatmaps.api.from
import io.beatmaps.common.Config
import io.beatmaps.common.api.EPlaylistType
import io.beatmaps.common.dbo.Playlist
import io.beatmaps.genericPage
import io.beatmaps.util.cdnPrefix
import io.ktor.http.HttpStatusCode
import io.ktor.server.locations.Location
import io.ktor.server.locations.get
import io.ktor.server.routing.Route
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
        val playlistData = transaction {
            Playlist.selectAll().where {
                (Playlist.id eq req.id) and Playlist.deletedAt.isNull() and (Playlist.type eq EPlaylistType.Public)
            }.limit(1).map { PlaylistFull.from(it, cdnPrefix()) }.firstOrNull()
        }

        genericPage(if (playlistData != null) HttpStatusCode.OK else HttpStatusCode.NotFound) {
            playlistData?.let {
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
