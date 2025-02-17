package io.beatmaps.api.playlist

import io.beatmaps.api.CuratePlaylist
import io.beatmaps.api.PlaylistApi
import io.beatmaps.api.PlaylistFull
import io.beatmaps.api.from
import io.beatmaps.common.UnCurateMapData
import io.beatmaps.common.UnCuratePlaylistData
import io.beatmaps.common.amqp.pub
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.dbo.ModLog
import io.beatmaps.common.dbo.Playlist
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.curatorAlias
import io.beatmaps.common.dbo.handleCurator
import io.beatmaps.common.dbo.handleUser
import io.beatmaps.common.dbo.joinCurator
import io.beatmaps.common.dbo.joinUser
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
import org.jetbrains.exposed.sql.update

fun Route.playlistCurate() {
    post<PlaylistApi.Curate> {
        requireAuthorization { _, user ->
            if (!user.isCurator()) {
                call.respond(HttpStatusCode.BadRequest)
            } else {
                val playlistUpdate = call.receive<CuratePlaylist>()

                val result = transaction {
                    fun curatePlaylist() =
                        Playlist.update({
                            (Playlist.id eq playlistUpdate.id) and (if (playlistUpdate.curated) Playlist.curatedAt.isNull() else Playlist.curatedAt.isNotNull()) and Playlist.deletedAt.isNull()
                        }) {
                            if (playlistUpdate.curated) {
                                it[curatedAt] = NowExpression(curatedAt)
                                it[curator] = user.userId
                            } else {
                                it[curatedAt] = null
                                it[curator] = null
                            }
                            it[updatedAt] = NowExpression(updatedAt)
                        }

                    (curatePlaylist() > 0).let { success ->
                        if (success) {
                            Playlist
                                .joinUser(Playlist.owner)
                                .joinCurator()
                                .select(Playlist.columns + User.columns + curatorAlias.columns + Playlist.Stats.all)
                                .where {
                                    (Playlist.id eq playlistUpdate.id) and Playlist.deletedAt.isNull()
                                }
                                .groupBy(Playlist.id, User.id, curatorAlias[User.id])
                                .handleUser()
                                .handleCurator()
                                .firstOrNull()
                                ?.let {
                                    PlaylistFull.from(it, cdnPrefix())
                                }
                                ?.also {
                                    if (!playlistUpdate.curated) {
                                        ModLog.insert(user.userId, null, UnCuratePlaylistData(playlistUpdate.id, playlistUpdate.reason), it.owner.id)
                                    }
                                }
                        } else {
                            null
                        }
                    }
                }?.also {
                    call.pub("beatmaps", "playlists.${it.playlistId}.updated.curation", null, it.playlistId)
                }

                call.respond(result ?: HttpStatusCode.BadRequest)
            }
        }
    }
}
