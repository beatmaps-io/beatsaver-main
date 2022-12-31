package io.beatmaps.api

import de.nielsfalk.ktor.swagger.DefaultValue
import de.nielsfalk.ktor.swagger.Ignore
import de.nielsfalk.ktor.swagger.get
import de.nielsfalk.ktor.swagger.ok
import de.nielsfalk.ktor.swagger.post
import de.nielsfalk.ktor.swagger.responds
import de.nielsfalk.ktor.swagger.version.shared.Group
import io.beatmaps.common.api.EPlaylistType
import io.beatmaps.common.db.wrapAsExpressionNotNull
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Playlist
import io.beatmaps.common.dbo.PlaylistMap
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.util.cdnPrefix
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.locations.Location
import io.ktor.server.locations.options
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.insertIgnoreAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

@Location("/api")
class BookmarksApi {
    @Group("Bookmarks")
    @Location("/bookmarks/add")
    data class Add(@Ignore val api: BookmarksApi)

    @Group("Bookmarks")
    @Location("/bookmarks/remove")
    data class Remove(@Ignore val api: BookmarksApi)

    @Group("Bookmarks")
    @Location("/bookmarks/{page}")
    data class Bookmarks(@Ignore val api: BookmarksApi, @DefaultValue("0") val page: Long = 0)
}

fun getNewId(userId: Int): Int? {
    return Playlist.insertIgnoreAndGetId {
        it[type] = EPlaylistType.System
        it[name] = "Bookmarks"
        it[description] = "Maps you've bookmarked, automatically updated."
        it[owner] = userId
    }?.value?.also { pl ->
        User.update({ User.id eq userId }) {
            it[bookmarksId] = pl
        }
    }
}

fun isBookmarked(mapId: Int, userId: Int): Boolean {
    return User
        .join(PlaylistMap, JoinType.LEFT, User.bookmarksId, PlaylistMap.playlistId)
        .select { (User.id eq userId) and (PlaylistMap.mapId eq mapId) }
        .count() > 0
}

fun Route.bookmarkRoute() {
    options<BookmarksApi.Add> {
        call.response.header("Access-Control-Allow-Origin", "*")
        call.respond(HttpStatusCode.OK)
    }

    post<BookmarksApi.Add, BookmarkRequest>("Add a bookmark".responds(ok<BookmarkUpdateResponse>())) { _, req ->
        call.response.header("Access-Control-Allow-Origin", "*")

        requireAuthorization { sess ->
            val mapId = req.mapId

            val added = transaction {
                val newId = getNewId(sess.userId)

                PlaylistMap.insertIgnore {
                    it[this.mapId] = mapId
                    it[order] = getMaxMapForUser(sess.userId)

                    if (newId != null) {
                        it[playlistId] = newId
                    } else {
                        it[playlistId] = wrapAsExpressionNotNull<Int>(
                            User
                                .slice(User.bookmarksId)
                                .select { User.id eq sess.userId }
                                .limit(1)
                        )
                    }
                }.insertedCount > 0
            }

            call.respond(BookmarkUpdateResponse(added))
        }
    }

    options<BookmarksApi.Remove> {
        call.response.header("Access-Control-Allow-Origin", "*")
        call.respond(HttpStatusCode.OK)
    }

    post<BookmarksApi.Remove, BookmarkRequest>("Remove a bookmark".responds(ok<BookmarkUpdateResponse>())) { _, req ->
        call.response.header("Access-Control-Allow-Origin", "*")

        requireAuthorization { sess ->
            val mapId = req.mapId

            val removed = transaction {
                PlaylistMap.deleteWhere {
                    (PlaylistMap.mapId eq mapId) and (
                        PlaylistMap.playlistId eqSubQuery
                            User
                                .slice(User.bookmarksId)
                                .select {
                                    User.id eq sess.userId
                                }
                        )
                } > 0
            }

            call.respond(BookmarkUpdateResponse(removed))
        }
    }

    options<BookmarksApi.Bookmarks> {
        call.response.header("Access-Control-Allow-Origin", "*")
        call.respond(HttpStatusCode.OK)
    }

    get<BookmarksApi.Bookmarks>("Get bookmarks from the logged-in user".responds(ok<BookmarkResponse>())) {
        call.response.header("Access-Control-Allow-Origin", "*")

        requireAuthorization { sess ->
            val maps = transaction {
                PlaylistMap
                    .join(User, JoinType.LEFT, PlaylistMap.playlistId, User.bookmarksId)
                    .join(Beatmap, JoinType.LEFT, PlaylistMap.mapId, Beatmap.id)
                    .slice(Beatmap.columns)
                    .select { User.id eq sess.userId }
                    .limit(it.page)
                    .complexToBeatmap()
                    .map {
                        MapDetail.from(it, cdnPrefix())
                    }
            }

            call.respond(BookmarkResponse(maps))
        }
    }
}
