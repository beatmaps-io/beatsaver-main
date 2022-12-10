package io.beatmaps.controllers

import io.beatmaps.api.PlaylistFull
import io.beatmaps.api.from
import io.beatmaps.common.Config
import io.beatmaps.common.dbo.Playlist
import io.beatmaps.genericPage
import io.beatmaps.util.cdnPrefix
import io.ktor.server.locations.Location
import io.ktor.server.locations.get
import io.ktor.server.routing.Route
import kotlinx.html.meta
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

@Location("/playlists") class PlaylistController {
    @Location("/{id}/edit") data class Edit(val id: Int, val api: PlaylistController)
    @Location("/new") data class New(val api: PlaylistController)
    @Location("/{id}") data class Detail(val id: Int, val api: PlaylistController)
}

fun Route.playlistController() {
    get<PlaylistController> {
        genericPage()
    }

    get<PlaylistController.New> {
        genericPage()
    }

    get<PlaylistController.Detail> {
        genericPage(
            headerTemplate = {
                transaction {
                    Playlist.select {
                        (Playlist.id eq it.id) and Playlist.deletedAt.isNull() and (Playlist.public eq true)
                    }.limit(1).map { PlaylistFull.from(it, cdnPrefix()) }.firstOrNull()
                }?.let {
                    meta("og:type", "website")
                    meta("og:site_name", "BeatSaver")
                    meta("og:title", it.name)
                    meta("og:url", "${Config.siteBase()}/playlists/${it.playlistId}")
                    meta("og:image", it.playlistImage)
                    meta("og:description", it.description.take(400))
                }
            }
        )
    }

    get<PlaylistController.Edit> {
        genericPage()
    }
}
