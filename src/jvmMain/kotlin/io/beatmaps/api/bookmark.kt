package io.beatmaps.api

import de.nielsfalk.ktor.swagger.DefaultValue
import de.nielsfalk.ktor.swagger.Ignore
import io.beatmaps.common.api.EPlaylistType
import io.beatmaps.common.db.wrapAsExpressionNotNull
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Playlist
import io.beatmaps.common.dbo.PlaylistMap
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.common.dbo.joinCurator
import io.beatmaps.common.dbo.joinUploader
import io.beatmaps.common.dbo.joinVersions
import io.beatmaps.common.dbo.reviewerAlias
import io.beatmaps.util.cdnPrefix
import io.ktor.server.application.call
import io.ktor.server.locations.Location
import io.ktor.server.locations.get
import io.ktor.server.locations.post
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.ExpressionWithColumnType
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.LiteralOp
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.insertIgnoreAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

@Location("/api")
class BookmarksApi {
    @Location("/bookmark")
    data class Bookmark(@Ignore val api: BookmarksApi)

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

fun addBookmark(mapId: ExpressionWithColumnType<EntityID<Int>>, userId: Int) = transaction {
    val newId = getNewId(userId)

    PlaylistMap.insertIgnore {
        it[this.mapId] = mapId
        it[order] = getMaxMapForUser(userId)

        if (newId != null) {
            it[playlistId] = newId
        } else {
            it[playlistId] = wrapAsExpressionNotNull<Int>(
                User
                    .slice(User.bookmarksId)
                    .select { User.id eq userId }
                    .limit(1)
            )
        }
    }.insertedCount
}

fun removeBookmark(mapId: ExpressionWithColumnType<EntityID<Int>>, userId: Int) = transaction {
    PlaylistMap.deleteWhere {
        (PlaylistMap.mapId eq mapId) and (
            PlaylistMap.playlistId eqSubQuery
                User
                    .slice(User.bookmarksId)
                    .select {
                        User.id eq userId
                    }
            )
    }
}

fun Route.bookmarkRoute() {
    post<BookmarksApi.Bookmark> {
        val req = call.receive<BookmarkRequest>()

        requireAuthorization("bookmarks") { sess ->
            val mapId = req.key?.toInt(16)
            val mapIdLiteral = mapId?.let { LiteralOp(PlaylistMap.id.columnType, EntityID(it, PlaylistMap)) }
            val mapIdOrSubquery = mapIdLiteral ?: req.hash?.let {
                wrapAsExpressionNotNull(Versions.slice(Versions.mapId).select { Versions.hash eq it }, PlaylistMap.id.columnType)
            }

            val updateCount =
                mapIdOrSubquery?.let { mapIdExp ->
                    if (req.bookmarked) {
                        addBookmark(mapIdExp, sess.userId)
                    } else {
                        removeBookmark(mapIdExp, sess.userId)
                    }
                } ?: 0

            call.respond(BookmarkUpdateResponse(updateCount > 0))
        }
    }

    get<BookmarksApi.Bookmarks> {
        requireAuthorization("bookmarks") { sess ->
            val maps = transaction {
                PlaylistMap
                    .join(reviewerAlias, JoinType.LEFT, PlaylistMap.playlistId, reviewerAlias[User.bookmarksId])
                    .join(Beatmap, JoinType.LEFT, PlaylistMap.mapId, Beatmap.id)
                    .joinVersions(false)
                    .joinUploader()
                    .joinCurator()
                    .select { reviewerAlias[User.id] eq sess.userId }
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
