@file:UseSerializers(OptionalPropertySerializer::class)

package io.beatmaps.api

import de.nielsfalk.ktor.swagger.DefaultValue
import de.nielsfalk.ktor.swagger.Description
import de.nielsfalk.ktor.swagger.Ignore
import de.nielsfalk.ktor.swagger.ModelClass
import io.beatmaps.common.OptionalProperty
import io.beatmaps.common.OptionalPropertySerializer
import io.beatmaps.common.amqp.pub
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
import io.beatmaps.common.or
import io.beatmaps.common.util.paramInfo
import io.beatmaps.common.util.requireParams
import io.beatmaps.util.cdnPrefix
import io.beatmaps.util.requireAuthorization
import io.ktor.resources.Resource
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.serialization.UseSerializers
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

@Resource("/api")
class BookmarksApi {
    @Resource("/bookmark")
    data class Bookmark(
        @Ignore
        val api: BookmarksApi
    )

    @Resource("/bookmarks/{page}")
    data class Bookmarks(
        @ModelClass(Long::class) @DefaultValue("0")
        val page: OptionalProperty<Long>? = OptionalProperty.NotPresent,
        @Description("Allowed values between 1 and 100") @ModelClass(Int::class) @DefaultValue("20")
        val pageSize: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        @Ignore
        val api: BookmarksApi
    ) {
        init {
            requireParams(
                paramInfo(Bookmarks::page), paramInfo(Bookmarks::pageSize)
            )
        }
    }
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
            it[songsChangedAt] = NowExpression(songsChangedAt)
        }
    ) {
        get(Playlist.id).value.also { playlistId ->
            User.update({ User.id eq userId }) {
                it[bookmarksId] = playlistId
                it[updatedAt] = NowExpression(updatedAt)
            }
        }
    }

fun mapIdForHash(hash: String) =
    Beatmap.joinVersions(false).select(Beatmap.id).where {
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
    post<BookmarksApi.Bookmark> {
        val req = call.receive<BookmarkRequest>()

        requireAuthorization(OauthScope.BOOKMARKS) { _, sess ->

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
        requireAuthorization(OauthScope.BOOKMARKS) { _, sess ->
            val maps = transaction {
                PlaylistMap
                    .join(reviewerAlias, JoinType.LEFT, PlaylistMap.playlistId, reviewerAlias[User.bookmarksId])
                    .join(Beatmap, JoinType.LEFT, PlaylistMap.mapId, Beatmap.id)
                    .joinVersions(false)
                    .joinUploader()
                    .joinCurator()
                    .selectAll()
                    .where { Beatmap.deletedAt.isNull() and (reviewerAlias[User.id] eq sess.userId) }
                    .orderBy(PlaylistMap.order)
                    .limit(it.page.or(0), it.pageSize.or(20).coerceIn(1, 100))
                    .complexToBeatmap()
                    .map {
                        MapDetail.from(it, cdnPrefix())
                    }
            }

            call.respond(BookmarkResponse(maps))
        }
    }
}
