package io.beatmaps.api.playlist

import de.nielsfalk.ktor.swagger.notFound
import de.nielsfalk.ktor.swagger.ok
import de.nielsfalk.ktor.swagger.responds
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapDetailWithOrder
import io.beatmaps.api.OauthScope
import io.beatmaps.api.Playlist
import io.beatmaps.api.PlaylistApi
import io.beatmaps.api.PlaylistCustomData
import io.beatmaps.api.PlaylistFull
import io.beatmaps.api.PlaylistPage
import io.beatmaps.api.PlaylistSong
import io.beatmaps.api.from
import io.beatmaps.api.getWithOptions
import io.beatmaps.api.limit
import io.beatmaps.api.notNull
import io.beatmaps.api.of
import io.beatmaps.api.search.PgSearchParams
import io.beatmaps.common.Folders
import io.beatmaps.common.SearchOrder
import io.beatmaps.common.SearchPlaylistConfig
import io.beatmaps.common.api.AiDeclarationType
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.api.EPlaylistType
import io.beatmaps.common.api.RankedFilter
import io.beatmaps.common.applyToQuery
import io.beatmaps.common.asQuery
import io.beatmaps.common.cleanString
import io.beatmaps.common.db.PgConcat
import io.beatmaps.common.db.greaterEqF
import io.beatmaps.common.db.lateral
import io.beatmaps.common.db.lessEqF
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Difficulty
import io.beatmaps.common.dbo.PlaylistMap
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.dbo.collaboratorAlias
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.common.dbo.curatorAlias
import io.beatmaps.common.dbo.handleCurator
import io.beatmaps.common.dbo.handleOwner
import io.beatmaps.common.dbo.joinBookmarked
import io.beatmaps.common.dbo.joinCollaborators
import io.beatmaps.common.dbo.joinCurator
import io.beatmaps.common.dbo.joinOwner
import io.beatmaps.common.dbo.joinPlaylistCurator
import io.beatmaps.common.dbo.joinUploader
import io.beatmaps.common.dbo.joinVersions
import io.beatmaps.util.cdnPrefix
import io.beatmaps.util.optionalAuthorization
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.locations.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.datetime.toJavaInstant
import org.jetbrains.exposed.sql.EqOp
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.intLiteral
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.util.Base64
import io.beatmaps.common.dbo.Playlist as PlaylistTable

fun Route.playlistSingle() {
    fun performSearchForPlaylist(config: SearchPlaylistConfig, cdnPrefix: String, page: Long, pageSize: Int = 20): List<MapDetailWithOrder> {
        val offset = page.toInt() * pageSize
        val actualPageSize = Integer.min(offset + pageSize, Integer.min(500, config.mapCount)) - offset

        if (actualPageSize <= 0 || actualPageSize > pageSize) return listOf()

        val params = config.searchParams
        val needsDiff = params.minNps != null || params.maxNps != null
        val searchFields = PgConcat(" ", Beatmap.name, Beatmap.description, Beatmap.levelAuthorName)
        val searchInfo = PgSearchParams.parseSearchQuery(params.search, searchFields, needsDiff)
        val actualSortOrder = searchInfo.validateSearchOrder(params.sortOrder)
        val sortArgs = searchInfo.sortArgsFor(actualSortOrder)

        return transaction {
            Beatmap
                .joinVersions(true)
                .joinUploader()
                .joinCurator()
                .joinCollaborators()
                .select(
                    (if (actualSortOrder == SearchOrder.Relevance) listOf(searchInfo.similarRank) else listOf()) +
                        Beatmap.columns + Versions.columns + Difficulty.columns + User.columns +
                        curatorAlias.columns + collaboratorAlias.columns
                )
                .where {
                    Beatmap.id.inSubQuery(
                        Beatmap
                            .joinUploader()
                            .crossJoin(
                                Versions
                                    .let { q ->
                                        if (needsDiff) q.join(Difficulty, JoinType.INNER, Versions.id, Difficulty.versionId) else q
                                    }
                                    .select(intLiteral(1))
                                    .where {
                                        EqOp(Versions.mapId, Beatmap.id) and (Versions.state eq EMapState.Published)
                                            .notNull(params.minNps) { o -> (Difficulty.nps greaterEqF o) }
                                            .notNull(params.maxNps) { o -> (Difficulty.nps lessEqF o) }
                                    }
                                    .limit(1)
                                    .lateral().alias("diff")
                            )
                            .select(Beatmap.id)
                            .where {
                                Beatmap.deletedAt.isNull()
                                    .let { q -> searchInfo.applyQuery(q) }
                                    .let { q ->
                                        // Doesn't quite make sense but we want to exclude beat sage by default
                                        when (params.automapper) {
                                            true -> q
                                            false -> q.and(Beatmap.declaredAi neq AiDeclarationType.None)
                                            null -> q.and(Beatmap.declaredAi eq AiDeclarationType.None)
                                        }
                                    }
                                    .notNull(searchInfo.userSubQuery) { o -> Beatmap.uploader inSubQuery o }
                                    .notNull(params.chroma) { o -> Beatmap.chroma eq o }
                                    .notNull(params.noodle) { o -> Beatmap.noodle eq o }
                                    .notNull(params.ranked) { o ->
                                        Op.of(o == RankedFilter.All).run {
                                            if (o.blRanked) this or Beatmap.blRanked else this
                                        }.run {
                                            if (o.ssRanked) this or Beatmap.ranked else this
                                        }
                                    }
                                    .notNull(params.curated) { o -> with(Beatmap.curatedAt) { if (o) isNotNull() else isNull() } }
                                    .notNull(params.verified) { o -> User.verifiedMapper eq o }
                                    .notNull(params.fullSpread) { o -> Beatmap.fullSpread eq o }
                                    .notNull(params.minNps) { o -> (Beatmap.maxNps greaterEqF o) }
                                    .notNull(params.maxNps) { o -> (Beatmap.minNps lessEqF o) }
                                    .notNull(params.from) { o -> Beatmap.uploaded greaterEq o.toJavaInstant() }
                                    .notNull(params.to) { o -> Beatmap.uploaded lessEq o.toJavaInstant() }
                                    .notNull(params.me) { o -> Beatmap.me eq o }
                                    .notNull(params.cinema) { o -> Beatmap.cinema eq o }
                                    .notNull(params.tags) { o -> o.asQuery().applyToQuery() }
                                    .let { q ->
                                        if (params.mappers.isEmpty()) q else q.and(Beatmap.uploader inList params.mappers)
                                    }
                            }
                            .orderBy(*sortArgs)
                            .limit(actualPageSize, offset.toLong())
                    )
                }
                .orderBy(*sortArgs)
                .complexToBeatmap()
                .mapIndexed { idx, m ->
                    MapDetailWithOrder(
                        MapDetail.from(m, cdnPrefix),
                        (offset + idx).toFloat()
                    )
                }
        }
    }

    fun getDetail(id: Int, cdnPrefix: String, userId: Int?, isAdmin: Boolean, page: Long?): PlaylistPage? {
        val detailPage = transaction {
            val playlist = PlaylistTable
                .joinMaps()
                .joinOwner()
                .joinPlaylistCurator()
                .select(PlaylistTable.columns + User.columns + curatorAlias.columns + PlaylistTable.Stats.all)
                .where {
                    (PlaylistTable.id eq id).let {
                        if (isAdmin) {
                            it
                        } else {
                            it and PlaylistTable.deletedAt.isNull()
                        }
                    }
                }
                .groupBy(PlaylistTable.id, User.id, curatorAlias[User.id])
                .handleOwner()
                .handleCurator()
                .firstOrNull()?.let {
                    PlaylistFull.from(it, cdnPrefix)
                }

            val mapsWithOrder = page?.let { page ->
                if (playlist?.type == EPlaylistType.Search && playlist.config is SearchPlaylistConfig) {
                    performSearchForPlaylist(playlist.config, cdnPrefix, page)
                } else {
                    val mapsSubQuery = PlaylistMap
                        .join(Beatmap, JoinType.INNER, PlaylistMap.mapId, Beatmap.id) { Beatmap.deletedAt.isNull() }
                        .joinVersions()
                        .select(PlaylistMap.mapId, PlaylistMap.order)
                        .where {
                            (PlaylistMap.playlistId eq id)
                        }
                        .orderBy(PlaylistMap.order)
                        .limit(page, 100)
                        .alias("subquery")

                    val orderList = mutableListOf<Float>()
                    val maps = mapsSubQuery
                        .join(Beatmap, JoinType.INNER, mapsSubQuery[PlaylistMap.mapId], Beatmap.id)
                        .joinVersions(true)
                        .joinUploader()
                        .joinCollaborators()
                        .joinCurator()
                        .joinBookmarked(userId)
                        .selectAll()
                        .where {
                            (Beatmap.deletedAt.isNull())
                        }
                        .complexToBeatmap {
                            orderList.add(it[mapsSubQuery[PlaylistMap.order]])
                        }
                        .map {
                            MapDetail.from(it, cdnPrefix)
                        }
                    maps.zip(orderList).map { MapDetailWithOrder(it.first, it.second) }
                }
            }

            PlaylistPage(playlist, mapsWithOrder)
        }

        return if (detailPage.playlist != null && (detailPage.playlist.type.anonymousAllowed || detailPage.playlist.owner.id == userId || isAdmin)) {
            detailPage
        } else {
            null
        }
    }

    get<PlaylistApi.Detail> { req ->
        optionalAuthorization(OauthScope.PLAYLISTS) { _, sess ->
            getDetail(req.id, cdnPrefix(), sess?.userId, sess?.isAdmin() == true, null)?.let { call.respond(it) } ?: call.respond(HttpStatusCode.NotFound)
        }
    }

    getWithOptions<PlaylistApi.DetailWithPage>("Get playlist detail".responds(ok<PlaylistPage>(), notFound())) { req ->
        optionalAuthorization(OauthScope.PLAYLISTS) { _, sess ->
            getDetail(req.id, cdnPrefix(), sess?.userId, sess?.isAdmin() == true, req.page)?.let { call.respond(it) } ?: call.respond(HttpStatusCode.NotFound)
        }
    }

    val bookmarksIcon = javaClass.classLoader.getResourceAsStream("assets/favicon/android-chrome-512x512.png")!!.readAllBytes()
    get<PlaylistApi.Download> { req ->
        val (playlist, playlistSongs) = transaction {
            fun getPlaylist() =
                PlaylistTable
                    .joinPlaylistCurator()
                    .joinOwner()
                    .selectAll()
                    .where {
                        (PlaylistTable.id eq req.id) and PlaylistTable.deletedAt.isNull()
                    }
                    .handleOwner()
                    .handleCurator()
                    .firstOrNull()?.let {
                        PlaylistFull.from(it, cdnPrefix())
                    }

            fun getMapsInPlaylist() =
                PlaylistMap
                    .join(Beatmap, JoinType.INNER, PlaylistMap.mapId, Beatmap.id)
                    .joinVersions()
                    .selectAll()
                    .where {
                        (PlaylistMap.playlistId eq req.id) and (Beatmap.deletedAt.isNull())
                    }
                    .orderBy(PlaylistMap.order)
                    .complexToBeatmap()
                    .mapNotNull { map ->
                        map.versions.values.firstOrNull { v -> v.state == EMapState.Published }?.let { v ->
                            PlaylistSong(
                                Integer.toHexString(map.id.value),
                                v.hash,
                                map.name
                            )
                        }
                    }

            val playlist = getPlaylist()

            if (playlist?.type == EPlaylistType.Search && playlist.config is SearchPlaylistConfig) {
                playlist to performSearchForPlaylist(playlist.config, cdnPrefix(), 0, 1000)
                    .mapNotNull {
                        it.map.publishedVersion()?.let { v ->
                            PlaylistSong(
                                it.map.id,
                                v.hash,
                                it.map.name
                            )
                        }
                    }
            } else {
                playlist to getMapsInPlaylist()
            }
        }

        optionalAuthorization(OauthScope.PLAYLISTS) { _, sess ->
            if (playlist != null && (playlist.type.anonymousAllowed || playlist.owner.id == sess?.userId)) {
                val localFile = when (playlist.type) {
                    EPlaylistType.System -> bookmarksIcon
                    else -> File(Folders.localPlaylistCoverFolder(), "${playlist.playlistId}.jpg").readBytes()
                }
                val imageStr = Base64.getEncoder().encodeToString(localFile)

                val cleanName = cleanString("BeatSaver - ${playlist.name}.bplist")
                call.response.headers.append(HttpHeaders.ContentDisposition, "attachment; filename=\"${cleanName}\"")
                call.respond(
                    Playlist(
                        playlist.name,
                        playlist.owner.name,
                        playlist.description,
                        imageStr,
                        PlaylistCustomData(playlist.downloadURL),
                        playlistSongs
                    )
                )
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}
