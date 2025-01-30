package io.beatmaps.api

import de.nielsfalk.ktor.swagger.DefaultValue
import de.nielsfalk.ktor.swagger.Description
import de.nielsfalk.ktor.swagger.Ignore
import de.nielsfalk.ktor.swagger.version.shared.Group
import io.beatmaps.api.playlist.playlistCreate
import io.beatmaps.api.playlist.playlistCurate
import io.beatmaps.api.playlist.playlistMaps
import io.beatmaps.api.playlist.playlistSearch
import io.beatmaps.api.playlist.playlistSingle
import io.beatmaps.common.SearchOrder
import io.beatmaps.common.db.wrapAsExpressionNotNull
import io.beatmaps.common.dbo.PlaylistMap
import io.beatmaps.common.dbo.User
import io.ktor.client.HttpClient
import io.ktor.server.locations.Location
import io.ktor.server.routing.Route
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.Coalesce
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.floatLiteral

const val prefix: String = "/playlists"

@Location("/api")
class PlaylistApi {
    @Location("$prefix/id/{id}")
    data class Detail(val id: Int, val api: PlaylistApi)

    @Group("Playlists")
    @Location("$prefix/id/{id}/{page}")
    data class DetailWithPage(val id: Int, @DefaultValue("0") val page: Long, @Ignore val api: PlaylistApi)

    @Location("$prefix/id/{id}/download/{filename?}")
    data class Download(val id: Int, val filename: String? = null, val api: PlaylistApi)

    @Location("$prefix/id/{id}/sign")
    data class OneClickSign(val id: Int, val api: PlaylistApi)

    @Location("$prefix/id/{id}/add")
    data class Add(val id: Int, val api: PlaylistApi)

    @Group("Playlists")
    @Location("$prefix/id/{id}/batch")
    data class Batch(val id: Int, @Ignore val api: PlaylistApi)

    @Location("$prefix/id/{id}/edit")
    data class Edit(val id: Int, val api: PlaylistApi)

    @Location("$prefix/create")
    data class Create(val api: PlaylistApi)

    @Location("$prefix/curate")
    data class Curate(val api: PlaylistApi)

    @Group("Playlists")
    @Location("$prefix/user/{userId}/{page}")
    data class ByUser(
        val userId: Int,
        val page: Long,
        @Ignore
        val basic: Boolean = false,
        @Ignore
        val api: PlaylistApi
    )

    @Group("Playlists")
    @Location("$prefix/map/{mapId}/{page}")
    data class ByMap(
        val mapId: String,
        val curated: Boolean? = null,
        val page: Long,
        @Ignore
        val basic: Boolean = false,
        @Ignore
        val api: PlaylistApi
    )

    @Group("Playlists")
    @Location("$prefix/latest")
    data class ByUploadDate(
        @Description("You probably want this. Supplying the uploaded time of the last map in the previous page will get you another page.\nYYYY-MM-DDTHH:MM:SS+00:00")
        val before: Instant? = null,
        @Description("Like `before` but will get you maps more recent than the time supplied.\nYYYY-MM-DDTHH:MM:SS+00:00")
        val after: Instant? = null,
        val sort: LatestPlaylistSort? = LatestPlaylistSort.CREATED,
        @Description("1 - 100") @DefaultValue("20")
        val pageSize: Int = 20,
        @Ignore
        val api: PlaylistApi
    )

    @Group("Playlists")
    @Location("$prefix/search/{page}")
    data class Solr(
        val q: String = "",
        @DefaultValue("0") val page: Long = 0,
        @Ignore val sortOrder: SearchOrder = SearchOrder.Relevance,
        val order: SearchOrder? = null,
        val minNps: Float? = null,
        val maxNps: Float? = null,
        val from: Instant? = null,
        val to: Instant? = null,
        val includeEmpty: Boolean? = null,
        val curated: Boolean? = null,
        val verified: Boolean? = null,
        @Ignore val seed: String? = null,
        @Ignore val api: PlaylistApi
    )

    @Group("Playlists")
    @Location("$prefix/search/v1/{page}")
    data class Text(
        val q: String = "",
        @DefaultValue("0") val page: Long = 0,
        @Ignore val sortOrder: SearchOrder = SearchOrder.Relevance,
        val order: SearchOrder? = null,
        val minNps: Float? = null,
        val maxNps: Float? = null,
        val from: Instant? = null,
        val to: Instant? = null,
        val includeEmpty: Boolean? = null,
        val curated: Boolean? = null,
        val verified: Boolean? = null,
        @Ignore val api: PlaylistApi
    )
}

enum class LatestPlaylistSort {
    UPDATED, SONGS_UPDATED, CREATED, CURATED
}

fun getMaxMapForUser(userId: Int) = Coalesce(
    wrapAsExpressionNotNull(
        PlaylistMap
            .join(User, JoinType.RIGHT, User.bookmarksId, PlaylistMap.playlistId)
            .select(PlaylistMap.order.plus(1f))
            .where {
                User.id eq userId
            }
            .orderBy(PlaylistMap.order, SortOrder.DESC)
            .limit(1),
        PlaylistMap.order.columnType
    ),
    floatLiteral(1f)
)

fun getMaxMap(id: Int) = Coalesce(
    wrapAsExpressionNotNull(
        PlaylistMap
            .select(PlaylistMap.order.plus(1f))
            .where {
                PlaylistMap.playlistId eq id
            }
            .orderBy(PlaylistMap.order, SortOrder.DESC)
            .limit(1),
        PlaylistMap.order.columnType
    ),
    floatLiteral(1f)
)

fun Route.playlistRoute(client: HttpClient) {
    playlistSearch()
    playlistSingle()
    playlistMaps()
    playlistCreate(client)
    playlistCurate()
}
