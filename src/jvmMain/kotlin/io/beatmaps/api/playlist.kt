@file:UseSerializers(LenientInstantSerializer::class, OptionalPropertySerializer::class)

package io.beatmaps.api

import de.nielsfalk.ktor.swagger.DefaultValue
import de.nielsfalk.ktor.swagger.Description
import de.nielsfalk.ktor.swagger.Ignore
import de.nielsfalk.ktor.swagger.ModelClass
import de.nielsfalk.ktor.swagger.version.shared.Group
import io.beatmaps.api.playlist.playlistCreate
import io.beatmaps.api.playlist.playlistCurate
import io.beatmaps.api.playlist.playlistMaps
import io.beatmaps.api.playlist.playlistSearch
import io.beatmaps.api.playlist.playlistSingle
import io.beatmaps.common.OptionalProperty
import io.beatmaps.common.OptionalPropertySerializer
import io.beatmaps.common.SearchOrder
import io.beatmaps.common.db.wrapAsExpressionNotNull
import io.beatmaps.common.dbo.PlaylistMap
import io.beatmaps.common.dbo.User
import io.beatmaps.common.util.LenientInstantSerializer
import io.beatmaps.common.util.paramInfo
import io.beatmaps.common.util.requireParams
import io.ktor.client.HttpClient
import io.ktor.resources.Resource
import io.ktor.server.routing.Route
import kotlinx.datetime.Instant
import kotlinx.serialization.UseSerializers
import org.jetbrains.exposed.sql.Coalesce
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.floatLiteral

const val prefix: String = "/playlists"

@Resource("/api")
class PlaylistApi {
    @Resource("$prefix/id/{id}")
    data class Detail(
        @ModelClass(Int::class)
        val id: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        @Ignore
        val api: PlaylistApi
    ) {
        init {
            requireParams(
                paramInfo(Detail::id)
            )
        }
    }

    @Group("Playlists")
    @Resource("$prefix/id/{id}/{page}")
    data class DetailWithPage(
        @ModelClass(Int::class)
        val id: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        @ModelClass(Long::class) @DefaultValue("0")
        val page: OptionalProperty<Long>? = OptionalProperty.NotPresent,
        @Ignore
        val api: PlaylistApi
    ) {
        init {
            requireParams(
                paramInfo(DetailWithPage::id), paramInfo(DetailWithPage::page)
            )
        }
    }

    @Resource("$prefix/id/{id}/download/{filename?}")
    data class Download(
        @ModelClass(Int::class)
        val id: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        val filename: String? = null,
        @Ignore
        val api: PlaylistApi
    ) {
        init {
            requireParams(
                paramInfo(Download::id)
            )
        }
    }

    @Resource("$prefix/id/{id}/sign")
    data class OneClickSign(
        @ModelClass(Int::class)
        val id: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        @Ignore
        val api: PlaylistApi
    ) {
        init {
            requireParams(
                paramInfo(OneClickSign::id)
            )
        }
    }

    @Resource("$prefix/id/{id}/add")
    data class Add(
        @ModelClass(Int::class)
        val id: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        @Ignore
        val api: PlaylistApi
    ) {
        init {
            requireParams(
                paramInfo(Add::id)
            )
        }
    }

    @Group("Playlists")
    @Resource("$prefix/id/{id}/batch")
    data class Batch(
        @ModelClass(Int::class)
        val id: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        @Ignore
        val api: PlaylistApi
    ) {
        init {
            requireParams(
                paramInfo(Batch::id)
            )
        }
    }

    @Resource("$prefix/id/{id}/edit")
    data class Edit(
        @ModelClass(Int::class)
        val id: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        @Ignore
        val api: PlaylistApi
    ) {
        init {
            requireParams(
                paramInfo(Edit::id)
            )
        }
    }

    @Resource("$prefix/create")
    data class Create(
        @Ignore
        val api: PlaylistApi
    )

    @Resource("$prefix/curate")
    data class Curate(
        @Ignore
        val api: PlaylistApi
    )

    @Group("Playlists")
    @Resource("$prefix/user/{userId}/{page}")
    data class ByUser(
        @ModelClass(Int::class)
        val userId: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        @ModelClass(Long::class)
        val page: OptionalProperty<Long>? = OptionalProperty.NotPresent,
        @Ignore
        val basic: Boolean = false,
        @Ignore
        val api: PlaylistApi
    ) {
        init {
            requireParams(
                paramInfo(ByUser::userId), paramInfo(ByUser::page)
            )
        }
    }

    @Group("Playlists")
    @Resource("$prefix/map/{mapId}/{page}")
    data class ByMap(
        val mapId: String,
        val curated: Boolean? = null,
        @ModelClass(Long::class)
        val page: OptionalProperty<Long>? = OptionalProperty.NotPresent,
        @Ignore
        val basic: Boolean = false,
        @Ignore
        val api: PlaylistApi
    ) {
        init {
            requireParams(
                paramInfo(ByMap::page)
            )
        }
    }

    @Group("Playlists")
    @Resource("$prefix/latest")
    data class ByUploadDate(
        @ModelClass(Instant::class) @Description("You probably want this. Supplying the uploaded time of the last map in the previous page will get you another page.\nYYYY-MM-DDTHH:MM:SS+00:00")
        val before: OptionalProperty<Instant>? = OptionalProperty.NotPresent,
        @ModelClass(Instant::class) @Description("Like `before` but will get you maps more recent than the time supplied.\nYYYY-MM-DDTHH:MM:SS+00:00")
        val after: OptionalProperty<Instant>? = OptionalProperty.NotPresent,
        @ModelClass(LatestPlaylistSort::class)
        val sort: OptionalProperty<LatestPlaylistSort>? = OptionalProperty.NotPresent,
        @ModelClass(Int::class) @Description("1 - 100") @DefaultValue("20")
        val pageSize: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        @Ignore
        val api: PlaylistApi
    ) {
        init {
            requireParams(
                paramInfo(ByUploadDate::before), paramInfo(ByUploadDate::after), paramInfo(ByUploadDate::sort), paramInfo(ByUploadDate::pageSize)
            )
        }
    }

    @Group("Playlists")
    @Resource("$prefix/search/{page}")
    data class Solr(
        val q: String = "",
        @ModelClass(Long::class) @DefaultValue("0")
        val page: OptionalProperty<Long>? = OptionalProperty.NotPresent,
        @Ignore @ModelClass(SearchOrder::class)
        val sortOrder: OptionalProperty<SearchOrder>? = OptionalProperty.NotPresent,
        @ModelClass(SearchOrder::class)
        val order: OptionalProperty<SearchOrder>? = OptionalProperty.NotPresent,
        @ModelClass(Float::class)
        val minNps: OptionalProperty<Float>? = OptionalProperty.NotPresent,
        @ModelClass(Float::class)
        val maxNps: OptionalProperty<Float>? = OptionalProperty.NotPresent,
        @ModelClass(Instant::class)
        val from: OptionalProperty<Instant>? = OptionalProperty.NotPresent,
        @ModelClass(Instant::class)
        val to: OptionalProperty<Instant>? = OptionalProperty.NotPresent,
        val includeEmpty: Boolean? = null,
        val curated: Boolean? = null,
        val verified: Boolean? = null,
        @Ignore
        val seed: String? = null,
        @Ignore
        val api: PlaylistApi
    ) {
        init {
            requireParams(
                paramInfo(Solr::page), paramInfo(Solr::sortOrder), paramInfo(Solr::order), paramInfo(Solr::minNps), paramInfo(Solr::maxNps), paramInfo(Solr::to)
            )
        }
    }

    @Group("Playlists")
    @Resource("$prefix/search/v1/{page}")
    data class Text(
        val q: String = "",
        @ModelClass(Long::class) @DefaultValue("0")
        val page: OptionalProperty<Long>? = OptionalProperty.NotPresent,
        @Ignore @ModelClass(SearchOrder::class)
        val sortOrder: OptionalProperty<SearchOrder>? = OptionalProperty.NotPresent,
        @ModelClass(SearchOrder::class)
        val order: OptionalProperty<SearchOrder>? = OptionalProperty.NotPresent,
        @ModelClass(Float::class)
        val minNps: OptionalProperty<Float>? = OptionalProperty.NotPresent,
        @ModelClass(Float::class)
        val maxNps: OptionalProperty<Float>? = OptionalProperty.NotPresent,
        @ModelClass(Instant::class)
        val from: OptionalProperty<Instant>? = OptionalProperty.NotPresent,
        @ModelClass(Instant::class)
        val to: OptionalProperty<Instant>? = OptionalProperty.NotPresent,
        val includeEmpty: Boolean? = null,
        val curated: Boolean? = null,
        val verified: Boolean? = null,
        @Ignore
        val api: PlaylistApi
    ) {
        init {
            requireParams(
                paramInfo(Text::page), paramInfo(Text::sortOrder), paramInfo(Text::order), paramInfo(Text::minNps), paramInfo(Text::maxNps), paramInfo(Text::to)
            )
        }
    }
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
