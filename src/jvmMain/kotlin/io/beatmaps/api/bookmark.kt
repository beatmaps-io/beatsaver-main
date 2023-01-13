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
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.insertIgnoreAndGetId
import org.jetbrains.exposed.sql.intLiteral
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

fun mapIdForHash(hash: String) =
    Beatmap.joinVersions(false).slice(Beatmap.id).select {
        Beatmap.deletedAt.isNull() and (Versions.hash eq hash)
    }.firstOrNull()?.let {
        it[Versions.mapId].value
    } ?: throw NotFoundException()

fun addBookmark(mapId: Int, userId: Int) = run {
    val newId = getNewId(userId)?.let { intLiteral(it) }

    PlaylistMap.insertIgnore {
        it[this.mapId] = mapId
        it[order] = getMaxMapForUser(userId)

        it[playlistId] = newId ?: wrapAsExpressionNotNull(
            User
                .slice(User.bookmarksId)
                .select { User.id eq userId }
                .limit(1)
        )
    }.insertedCount
}

fun addBookmark(hash: String, userId: Int) = addBookmark(mapIdForHash(hash), userId)

fun removeBookmark(mapId: Int, userId: Int) = run {
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
fun removeBookmark(hash: String, userId: Int) = removeBookmark(mapIdForHash(hash), userId)

fun Route.bookmarkRoute() {
    post<BookmarksApi.Bookmark> {
        val req = call.receive<BookmarkRequest>()

        requireAuthorization("bookmarks") { sess ->
            val mapId = req.key?.toIntOrNull(16)

            val updateCount = transaction {
                if (req.bookmarked) {
                    if (mapId != null) {
                        addBookmark(mapId, sess.userId)
                    } else if (req.hash != null) {
                        addBookmark(req.hash, sess.userId)
                    } else 0
                } else {
                    if (mapId != null) {
                        removeBookmark(mapId, sess.userId)
                    } else if (req.hash != null) {
                        removeBookmark(req.hash, sess.userId)
                    } else 0
                }
            }

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
