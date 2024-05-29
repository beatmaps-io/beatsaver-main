package io.beatmaps.api.playlist

import de.nielsfalk.ktor.swagger.ok
import de.nielsfalk.ktor.swagger.post
import de.nielsfalk.ktor.swagger.responds
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.OauthScope
import io.beatmaps.api.PlaylistApi
import io.beatmaps.api.PlaylistBatchRequest
import io.beatmaps.api.PlaylistMapRequest
import io.beatmaps.api.getMaxMap
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.db.updateReturning
import io.beatmaps.common.db.upsert
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Playlist
import io.beatmaps.common.dbo.PlaylistDao
import io.beatmaps.common.dbo.PlaylistMap
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.dbo.joinVersions
import io.beatmaps.common.pub
import io.beatmaps.util.requireAuthorization
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.locations.post
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.floatLiteral
import org.jetbrains.exposed.sql.intLiteral
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.transactions.transaction
import org.postgresql.util.PSQLException

fun Route.playlistMaps() {
    class PlaylistChangeException(msg: String) : Exception(msg)
    fun <T> catchNullRelation(block: () -> T) = try {
        block()
    } catch (e: ExposedSQLException) {
        val cause = e.cause
        if (cause is PSQLException && cause.sqlState == "23502") {
            // Set value is null. Map not found
            throw PlaylistChangeException("Cancel transaction")
        }

        throw e
    }

    fun applyPlaylistChange(pId: Int, inPlaylist: Boolean, mapId: Expression<Int>, newOrder: Float? = null) =
        catchNullRelation {
            if (inPlaylist) {
                PlaylistMap.upsert(conflictIndex = PlaylistMap.link) {
                    it[playlistId] = pId
                    it[PlaylistMap.mapId] = mapId
                    it[order] = newOrder?.let { f -> floatLiteral(f) } ?: getMaxMap(pId)
                }

                true
            } else {
                PlaylistMap.deleteWhere {
                    (playlistId eq pId) and (PlaylistMap.mapId eq mapId)
                }.let { res -> res > 0 }
            }
        }

    fun applyPlaylistChange(pId: Int, inPlaylist: Boolean, mapId: Int, newOrder: Float? = null) =
        applyPlaylistChange(pId, inPlaylist, intLiteral(mapId), newOrder)

    post<PlaylistApi.Batch, PlaylistBatchRequest>("Add or remove up to 100 maps to a playlist. Requires OAUTH".responds(ok<ActionResponse>())) { req, pbr ->
        requireAuthorization(OauthScope.MANAGE_PLAYLISTS) { _, sess ->
            val validKeys = (pbr.keys ?: listOf()).mapNotNull { key -> key.toIntOrNull(16) }
            val hashesOrEmpty = pbr.hashes?.map { it.lowercase() } ?: listOf()
            if (hashesOrEmpty.size + validKeys.size > 100) {
                call.respond(HttpStatusCode.BadRequest, "Too many maps")
                return@requireAuthorization
            } else if (hashesOrEmpty.size + validKeys.size <= 0 || (validKeys.size != (pbr.keys?.size ?: 0) && pbr.ignoreUnknown != true)) {
                // No hashes or keys
                // OR
                // Some invalid keys but not allowed to ignore unknown
                call.respond(HttpStatusCode.BadRequest, "Nothing to do")
                return@requireAuthorization
            }

            try {
                transaction {
                    Playlist
                        .updateReturning(
                            {
                                (Playlist.id eq req.id) and (Playlist.owner eq sess.userId) and Playlist.deletedAt.isNull()
                            },
                            {
                                it[songsChangedAt] = NowExpression(songsChangedAt.columnType)
                            },
                            *Playlist.columns.toTypedArray()
                        )?.firstOrNull()?.let { row ->
                            val playlist = PlaylistDao.wrapRow(row)
                            val maxMap =
                                PlaylistMap
                                    .select(PlaylistMap.order)
                                    .where {
                                        PlaylistMap.playlistId eq playlist.id.value
                                    }
                                    .orderBy(PlaylistMap.order, SortOrder.DESC)
                                    .limit(1)
                                    .firstOrNull()
                                    ?.let {
                                        it[PlaylistMap.order]
                                    } ?: 0f

                            val lookup = Beatmap
                                .joinVersions(false, state = null)
                                .select(Versions.hash, Beatmap.id)
                                .where {
                                    Beatmap.deletedAt.isNull() and (Beatmap.id.inList(validKeys) or Versions.hash.inList(hashesOrEmpty))
                                }.associate {
                                    it[Versions.hash].lowercase() to it[Beatmap.id].value
                                }
                            val unorderedMapIds = lookup.values.toSet()

                            val mapIds = validKeys.filter { unorderedMapIds.contains(it) } +
                                hashesOrEmpty.mapNotNull { if (lookup.containsKey(it) && !validKeys.contains(lookup[it])) lookup[it] else null }

                            val result = if (mapIds.size != (hashesOrEmpty + validKeys).size && pbr.ignoreUnknown != true) {
                                rollback()
                                null
                            } else if (pbr.inPlaylist == true) {
                                PlaylistMap.batchInsert(mapIds.mapIndexed { idx, it -> idx to it }, true, shouldReturnGeneratedValues = false) {
                                    this[PlaylistMap.playlistId] = playlist.id
                                    this[PlaylistMap.mapId] = it.second
                                    this[PlaylistMap.order] = maxMap + it.first + 1
                                }.size
                            } else {
                                PlaylistMap.deleteWhere {
                                    (playlistId eq playlist.id.value) and (mapId.inList(mapIds))
                                }
                            }

                            result?.let {
                                if (it > 0) playlist.id.value else 0
                            }
                        }
                }
            } catch (_: PlaylistChangeException) {
                null
            }.let {
                when (it) {
                    null -> call.respond(HttpStatusCode.NotFound)
                    0 -> call.respond(ActionResponse(false))
                    else -> {
                        call.pub("beatmaps", "playlists.$it.updated", null, it)
                        call.respond(ActionResponse(true))
                    }
                }
            }
        }
    }

    post<PlaylistApi.Add> { req ->
        requireAuthorization(OauthScope.MANAGE_PLAYLISTS) { _, sess ->
            val pmr = call.receive<PlaylistMapRequest>()
            try {
                transaction {
                    Playlist
                        .updateReturning(
                            {
                                (Playlist.id eq req.id) and (Playlist.owner eq sess.userId) and Playlist.deletedAt.isNull()
                            },
                            {
                                it[songsChangedAt] = NowExpression(songsChangedAt.columnType)
                            },
                            *Playlist.columns.toTypedArray()
                        )?.firstOrNull()?.let { row ->
                            val playlist = PlaylistDao.wrapRow(row)
                            val newOrder = pmr.order

                            // Only perform these operations once we've verified the owner is logged in
                            // and the playlist exists (as above)
                            if (applyPlaylistChange(playlist.id.value, pmr.inPlaylist == true, pmr.mapId.toInt(16), newOrder)) {
                                playlist.id.value
                            } else {
                                0
                            }
                        }
                }
            } catch (_: PlaylistChangeException) {
                null
            } catch (_: NumberFormatException) {
                null
            }.let {
                when (it) {
                    null -> call.respond(HttpStatusCode.NotFound)
                    0 -> call.respond(ActionResponse(false))
                    else -> {
                        call.pub("beatmaps", "playlists.$it.updated", null, it)
                        call.respond(ActionResponse(true))
                    }
                }
            }
        }
    }
}
