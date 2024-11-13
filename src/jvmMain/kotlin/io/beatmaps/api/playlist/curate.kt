package io.beatmaps.api.playlist

import io.beatmaps.api.CuratePlaylist
import io.beatmaps.api.PlaylistApi
import io.beatmaps.api.from
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.db.updateReturning
import io.beatmaps.common.dbo.Playlist
import io.beatmaps.util.cdnPrefix
import io.beatmaps.util.requireAuthorization
import io.ktor.server.application.call
import io.ktor.server.locations.post
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.playlistCurate() {
    post<PlaylistApi.Curate> {
        requireAuthorization { _, user ->
            if (!user.isCurator()) {
                call.respond(io.ktor.http.HttpStatusCode.BadRequest)
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
                                it[curator] = EntityID(user.userId, io.beatmaps.common.dbo.User)
                            } else {
                                it[curatedAt] = null
                                it[curator] = null
                            }
                            it[updatedAt] = NowExpression(updatedAt)
                        },
                        *io.beatmaps.common.dbo.Playlist.columns.toTypedArray()
                    )?.firstOrNull()?.let {
                        io.beatmaps.api.PlaylistFull.from(it, cdnPrefix())
                    }
                }

                call.respond(result ?: io.ktor.http.HttpStatusCode.BadRequest)
            }
        }
    }
}
