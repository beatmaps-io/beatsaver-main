package io.beatmaps.api.playlist

import de.nielsfalk.ktor.swagger.notFound
import de.nielsfalk.ktor.swagger.ok
import de.nielsfalk.ktor.swagger.responds
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapDetailWithOrder
import io.beatmaps.api.OauthScope
import io.beatmaps.api.Playlist
import io.beatmaps.api.PlaylistApi
import io.beatmaps.api.PlaylistConstants
import io.beatmaps.api.PlaylistCustomData
import io.beatmaps.api.PlaylistFull
import io.beatmaps.api.PlaylistPage
import io.beatmaps.api.PlaylistSong
import io.beatmaps.api.applyToQuery
import io.beatmaps.api.from
import io.beatmaps.api.limit
import io.beatmaps.api.notNull
import io.beatmaps.api.search.SolrSearchParams
import io.beatmaps.api.util.getWithOptions
import io.beatmaps.common.Config
import io.beatmaps.common.Folders
import io.beatmaps.common.SearchPlaylistConfig
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.api.EPlaylistType
import io.beatmaps.common.asQuery
import io.beatmaps.common.cleanString
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Difficulty
import io.beatmaps.common.dbo.PlaylistDao
import io.beatmaps.common.dbo.PlaylistMap
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.dbo.bookmark
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
import io.beatmaps.common.solr.collections.BsSolr
import io.beatmaps.common.solr.field.anyOf
import io.beatmaps.common.solr.field.apply
import io.beatmaps.common.solr.field.eq
import io.beatmaps.common.solr.field.greaterEq
import io.beatmaps.common.solr.field.inList
import io.beatmaps.common.solr.field.lessEq
import io.beatmaps.common.solr.getIds
import io.beatmaps.controllers.CdnSig
import io.beatmaps.login.Session
import io.beatmaps.util.cdnPrefix
import io.beatmaps.util.optionalAuthorization
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.locations.get
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.util.Base64
import kotlin.time.Duration.Companion.seconds
import io.beatmaps.common.dbo.Playlist as PlaylistTable

fun Route.playlistSingle() {
    suspend fun performSearchForPlaylist(playlistId: Int, userId: Int?, config: SearchPlaylistConfig, cdnPrefix: String, page: Long, pageSize: Int, call: ApplicationCall): List<MapDetailWithOrder> {
        val offset = page.toInt() * pageSize
        val actualPageSize = Integer.min(offset + pageSize, Integer.min(PlaylistConstants.MAX_SEARCH_MAPS, config.mapCount)) - offset

        if (actualPageSize <= 0 || actualPageSize > pageSize) return listOf()

        val params = config.searchParams
        val searchInfo = SolrSearchParams.parseSearchQuery(params.search)
        val actualSortOrder = searchInfo.validateSearchOrder(params.sortOrder)

        return newSuspendedTransaction {
            val results = BsSolr.newQuery(actualSortOrder)
                .let { q ->
                    searchInfo.applyQuery(q)
                }
                .also { q ->
                    when (params.automapper) {
                        true -> null
                        false -> BsSolr.ai eq true
                        null -> BsSolr.ai eq false
                    }?.let { filter ->
                        q.apply(filter)
                    }
                }
                .also { q ->
                    val mapperIds = params.mappers + (searchInfo.userSubQuery?.map { it[User.id].value } ?: listOf())
                    q.apply(BsSolr.mapperIds inList mapperIds)
                }
                .notNull(params.chroma) { o ->
                    val chromaQuery = (BsSolr.suggestions eq "Chroma") or (BsSolr.requirements eq "Chroma")
                    if (o) chromaQuery else chromaQuery.not()
                }
                .notNull(params.noodle) { o ->
                    val noodleQuery = BsSolr.requirements eq "Noodle Extensions"
                    if (o) noodleQuery else noodleQuery.not()
                }
                .notNull(params.me) { o ->
                    val meQuery = BsSolr.requirements eq "Mapping Extensions"
                    if (o) meQuery else meQuery.not()
                }
                .notNull(params.cinema) { o ->
                    val cinemaQuery = (BsSolr.suggestions eq "Cinema") or (BsSolr.requirements eq "Cinema")
                    if (o) cinemaQuery else cinemaQuery.not()
                }
                .apply {
                    listOfNotNull(
                        if (params.ranked.blRanked) BsSolr.rankedbl eq true else null,
                        if (params.ranked.ssRanked) BsSolr.rankedss eq true else null
                    ).anyOf()
                }
                .notNull(params.curated) { o -> BsSolr.curated.any().let { if (o) it else it.not() } }
                .notNull(params.verified) { o -> BsSolr.verified eq o }
                .notNull(params.fullSpread) { o -> BsSolr.fullSpread eq o }
                .notNull(params.minNps) { o -> BsSolr.nps greaterEq o }
                .notNull(params.maxNps) { o -> BsSolr.nps lessEq o }
                .notNull(params.from) { o -> BsSolr.uploaded greaterEq o }
                .notNull(params.to) { o -> BsSolr.uploaded lessEq o }
                .also { q ->
                    params.tags.asQuery().applyToQuery(q)
                }
                .apply {
                    BsSolr.environment inList params.environments.map { e -> e.name }
                }
                .let { q ->
                    BsSolr.addSortArgs(q, playlistId, actualSortOrder)
                }
                .setStart(offset).setRows(actualPageSize)
                .getIds(BsSolr, call = call)

            Beatmap
                .joinVersions(true)
                .joinUploader()
                .joinCurator()
                .joinCollaborators()
                .joinBookmarked(userId)
                .select(
                    Beatmap.columns + Versions.columns + Difficulty.columns + User.columns +
                        curatorAlias.columns + bookmark.columns + collaboratorAlias.columns
                )
                .where {
                    Beatmap.id.inList(results.mapIds)
                }
                .complexToBeatmap()
                .sortedBy { results.order[it.id.value] } // Match order from solr
                .mapIndexed { idx, m ->
                    MapDetailWithOrder(
                        MapDetail.from(m, cdnPrefix),
                        (offset + idx).toFloat()
                    )
                }
        }
    }

    suspend fun getDetail(id: Int, cdnPrefix: String, userId: Int?, isAdmin: Boolean, page: Long?, call: ApplicationCall): PlaylistPage? {
        val detailPage = newSuspendedTransaction {
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
                    performSearchForPlaylist(playlist.playlistId, userId, playlist.config, cdnPrefix, page, PlaylistConstants.PAGE_SIZE, call)
                } else {
                    val mapsSubQuery = PlaylistMap
                        .join(Beatmap, JoinType.INNER, PlaylistMap.mapId, Beatmap.id) { Beatmap.deletedAt.isNull() }
                        .joinVersions()
                        .select(PlaylistMap.mapId, PlaylistMap.order)
                        .where {
                            (PlaylistMap.playlistId eq id)
                        }
                        .orderBy(PlaylistMap.order)
                        .limit(page, PlaylistConstants.PAGE_SIZE)
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

    getWithOptions<PlaylistApi.Detail> { req ->
        optionalAuthorization(OauthScope.PLAYLISTS) { _, sess ->
            getDetail(req.id, cdnPrefix(), sess?.userId, sess?.isAdmin() == true, null, call)?.let { call.respond(it) } ?: call.respond(HttpStatusCode.NotFound)
        }
    }

    getWithOptions<PlaylistApi.DetailWithPage>("Get playlist detail".responds(ok<PlaylistPage>(), notFound())) { req ->
        optionalAuthorization(OauthScope.PLAYLISTS) { _, sess ->
            getDetail(req.id, cdnPrefix(), sess?.userId, sess?.isAdmin() == true, req.page, call)?.let { call.respond(it) } ?: call.respond(HttpStatusCode.NotFound)
        }
    }

    val bookmarksIcon = javaClass.classLoader.getResourceAsStream("assets/favicon/android-chrome-512x512.png")!!.readAllBytes()
    get<PlaylistApi.Download> { req ->
        val signed = CdnSig.verify("playlist-${req.id}", call.request)

        val (playlist, playlistSongs) = newSuspendedTransaction {
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
                playlist to performSearchForPlaylist(playlist.playlistId, null, playlist.config, cdnPrefix(), 0, PlaylistConstants.MAX_SEARCH_MAPS, call)
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
            if (playlist != null && (signed || playlist.type.anonymousAllowed || playlist.owner.id == sess?.userId)) {
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

    get<PlaylistApi.OneClickSign> { req ->
        val sess = call.sessions.get<Session>()

        transaction {
            PlaylistTable
                .selectAll()
                .where {
                    (PlaylistTable.id eq req.id) and PlaylistTable.deletedAt.isNull()
                }
                .firstOrNull()
                ?.let { PlaylistDao.wrapRow(it) }
        }?.let { playlist ->
            val sig = if (!playlist.type.anonymousAllowed && playlist.ownerId.value == sess?.userId) {
                val exp = Clock.System.now().plus(60.seconds).epochSeconds
                "?" + CdnSig.queryParams("playlist-${req.id}", exp)
            } else {
                ""
            }

            val url = "bsplaylist://playlist/${Config.apiBase(true)}/playlists/id/${playlist.id.value}/download$sig"

            call.respondRedirect(url)
        }
    }
}
