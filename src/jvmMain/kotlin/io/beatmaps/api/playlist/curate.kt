package io.beatmaps.api.playlist

import io.beatmaps.api.CuratePlaylist
import io.beatmaps.api.PlaylistApi
import io.beatmaps.api.PlaylistFull
import io.beatmaps.api.from
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.db.updateReturning
import io.beatmaps.common.dbo.Playlist
import io.beatmaps.common.pub
import io.beatmaps.util.cdnPrefix
import io.beatmaps.util.requireAuthorization
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.locations.post
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.playlistCurate() {
    post<PlaylistApi.Curate> {
        requireAuthorization { _, user ->
            if (!user.isCurator()) {
                call.respond(HttpStatusCode.BadRequest)
            } else {
                val playlistUpdate = call.receive<CuratePlaylist>()

                val result = transaction {
                    Playlist.updateReturning(
                        {
                            (Playlist.id eq playlistUpdate.id) and (if (playlistUpdate.curated) Playlist.curatedAt.isNull() else Playlist.curatedAt.isNotNull()) and Playlist.deletedAt.isNull()
                        },
                        {
                            if (playlistUpdate.curated) {
                                it[curatedAt] = NowExpression(curatedAt)
                                it[curator] = user.userId
                            } else {
                                it[curatedAt] = null
                                it[curator] = null
                            }
                            it[updatedAt] = NowExpression(updatedAt)
                        },
                        *Playlist.columns.toTypedArray()
                    )?.firstOrNull()?.let {
                        PlaylistFull.from(it, cdnPrefix())
                    }
                }?.also {
                    call.pub("beatmaps", "playlists.${it.playlistId}.updated.curation", null, it.playlistId)
                }

                call.respond(result ?: HttpStatusCode.BadRequest)
            }
        }
    }
}
