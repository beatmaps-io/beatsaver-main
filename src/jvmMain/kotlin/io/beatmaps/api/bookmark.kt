package io.beatmaps.api

import io.beatmaps.common.api.EPlaylistType
import io.beatmaps.common.dbo.Playlist
import io.beatmaps.common.dbo.PlaylistMap
import io.beatmaps.common.dbo.User
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.locations.Location
import io.ktor.server.locations.post
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

@Location("/api/bookmarks") class BookmarksApi {
    @Location("/add") data class Add(val api: BookmarksApi)
    @Location("/remove") data class Remove(val api: BookmarksApi)
}

fun getBookmarksId(userId: Int): Int {
    var id = User
        .slice(User.bookmarksId)
        .select { User.id eq userId }
        .first()[User.bookmarksId]?.value

    if (id == null) {
        id = Playlist.insertAndGetId {
            it[type] = EPlaylistType.System
            it[name] = "Bookmarks"
            it[description] = "Maps you've bookmarked, automatically updated."
            it[owner] = userId
        }.value

        User.update({ User.id eq userId }) {
            it[bookmarksId] = id
        }
    }

    return id
}

fun isBookMarked(mapId: Int, userId: Int): Boolean {
    val bookmarksId = getBookmarksId(userId)

    return PlaylistMap
        .select { (PlaylistMap.mapId eq mapId) and (PlaylistMap.playlistId eq bookmarksId) }
        .count() > 0
}

fun Route.bookmarkRoute() {
    post<BookmarksApi.Add> {
        requireAuthorization { sess ->
            val mapId = call.receive<BookmarkRequest>().mapId

            val added = transaction {
                val exists = isBookMarked(mapId, sess.userId)

                if (!exists) {
                    val bookmarksId = getBookmarksId(sess.userId)

                    PlaylistMap.insert {
                        it[this.mapId] = mapId
                        it[playlistId] = bookmarksId
                        it[order] = getMaxMap(bookmarksId)?.order?.plus(1) ?: 1.0f
                    }
                }

                !exists
            }

            call.respond(if (added) HttpStatusCode.OK else HttpStatusCode.NotModified)
        }
    }

    post<BookmarksApi.Remove> {
        requireAuthorization { sess ->
            val mapId = call.receive<BookmarkRequest>().mapId

            val removed = transaction {
                val exists = isBookMarked(mapId, sess.userId)

                if (exists) {
                    val bookmarksId = getBookmarksId(sess.userId)

                    PlaylistMap.deleteWhere {
                        (PlaylistMap.mapId eq mapId) and (PlaylistMap.playlistId eq bookmarksId)
                    }
                }

                exists
            }

            call.respond(if (removed) HttpStatusCode.OK else HttpStatusCode.NotModified)
        }
    }
}
