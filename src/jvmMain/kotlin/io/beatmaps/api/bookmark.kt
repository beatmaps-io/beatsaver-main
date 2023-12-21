package io.beatmaps.api

import de.nielsfalk.ktor.swagger.DefaultValue
import de.nielsfalk.ktor.swagger.Description
import de.nielsfalk.ktor.swagger.Ignore
import io.beatmaps.common.api.EPlaylistType
import io.beatmaps.common.db.ConflictType
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.db.upsertCustom
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
import io.beatmaps.common.pub
import io.beatmaps.util.cdnPrefix
import io.ktor.server.application.call
import io.ktor.server.locations.Location
import io.ktor.server.locations.get
import io.ktor.server.locations.post
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.logging.Logger

@Location("/api")
class BookmarksApi {
    @Location("/bookmark")
    data class Bookmark(@Ignore val api: BookmarksApi)

    @Location("/bookmarks/{page}")
    data class Bookmarks(
        @Ignore val api: BookmarksApi,
        @DefaultValue("0") val page: Long = 0,
        @Description("Allowed values between 1 and 100")
        @DefaultValue("20") val pageSize: Int = 20
    )
}

object BookmarksConflict : ConflictType {
    override fun StringBuilder.prepareSQL() {
        append(" ON CONFLICT(\"")
        append(Playlist.owner.name)
        append("\") WHERE ")
        append(Playlist.type.name)
        append(" = 'System'")
    }

    override fun shouldUpdate(column: Column<*>) = column == Playlist.songsChangedAt

    override val returning: List<Column<*>>
        get() = listOf(Playlist.id)
}

fun getNewId(userId: Int) =
    with(
        Playlist.upsertCustom(BookmarksConflict) {
            it[type] = EPlaylistType.System
            it[name] = "Bookmarks"
            it[description] = "Maps you've bookmarked, automatically updated."
            it[owner] = userId
            it[songsChangedAt] = NowExpression(songsChangedAt.columnType)
        }
    ) {
        get(Playlist.id).value.also { playlistId ->
            User.update({ User.id eq userId }) {
                it[bookmarksId] = playlistId
            }
        }
    }

fun mapIdForHash(hash: String) =
    Beatmap.joinVersions(false).slice(Beatmap.id).select {
        Beatmap.deletedAt.isNull() and (Versions.hash eq hash.lowercase())
    }.firstOrNull()?.let {
        it[Beatmap.id].value
    } ?: throw NotFoundException()

fun addBookmark(mapId: Int, userId: Int, playlist: Int) =
    PlaylistMap.insertIgnore {
        it[this.mapId] = mapId
        it[order] = getMaxMapForUser(userId)

        it[playlistId] = playlist
    }.insertedCount

fun removeBookmark(mapId: Int, playlistId: Int) =
    PlaylistMap.deleteWhere {
        (PlaylistMap.mapId eq mapId) and (PlaylistMap.playlistId eq playlistId)
    }

fun Route.bookmarkRoute() {
    val logger = Logger.getLogger("bmio.routes.bookmarkRoute")

    post<BookmarksApi.Bookmark> {
        val req = call.receive<BookmarkRequest>()

        requireAuthorization(OauthScope.BOOKMARKS) { sess ->

            val (updateCount, playlistId) = transaction {
                (req.key?.toIntOrNull(16) ?: req.hash?.let { mapIdForHash(it) })?.let { mapId ->
                    val playlistId = getNewId(sess.userId)

                    if (req.bookmarked) {
                        addBookmark(mapId, sess.userId, playlistId)
                    } else {
                        removeBookmark(mapId, playlistId)
                    } to playlistId
                } ?: (0 to null)
            }

            if (playlistId != null) {
                call.pub("beatmaps", "playlists.$playlistId.updated", null, playlistId)
            }

            call.respond(BookmarkUpdateResponse(updateCount > 0))
        }
    }

    get<BookmarksApi.Bookmarks> {
        requireAuthorization(OauthScope.BOOKMARKS) { sess ->
            try {
                val maps = transaction {
                    PlaylistMap
                        .join(reviewerAlias, JoinType.LEFT, PlaylistMap.playlistId, reviewerAlias[User.bookmarksId])
                        .join(Beatmap, JoinType.LEFT, PlaylistMap.mapId, Beatmap.id)
                        .joinVersions(false)
                        .joinUploader()
                        .joinCurator()
                        .select { Beatmap.deletedAt.isNull() and (reviewerAlias[User.id] eq sess.userId) }
                        .orderBy(PlaylistMap.order)
                        .limit(it.page, it.pageSize.coerceIn(1, 100))
                        .complexToBeatmap()
                        .map {
                            MapDetail.from(it, cdnPrefix())
                        }
                }

                call.respond(BookmarkResponse(maps))
            } catch (e: NullPointerException) {
                logger.severe { "Error getting bookmarks for ${sess.userId}, page ${it.page} (${it.pageSize})" }
                e.printStackTrace()
            }
        }
    }
}
