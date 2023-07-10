package io.beatmaps.api

import de.nielsfalk.ktor.swagger.DefaultValue
import de.nielsfalk.ktor.swagger.Description
import de.nielsfalk.ktor.swagger.Ignore
import de.nielsfalk.ktor.swagger.get
import de.nielsfalk.ktor.swagger.notFound
import de.nielsfalk.ktor.swagger.ok
import de.nielsfalk.ktor.swagger.post
import de.nielsfalk.ktor.swagger.responds
import de.nielsfalk.ktor.swagger.version.shared.Group
import io.beatmaps.common.DeletedPlaylistData
import io.beatmaps.common.EditPlaylistData
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.api.EPlaylistType
import io.beatmaps.common.cleanString
import io.beatmaps.common.copyToSuspend
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.db.PgConcat
import io.beatmaps.common.db.greaterEqF
import io.beatmaps.common.db.lessEqF
import io.beatmaps.common.db.updateReturning
import io.beatmaps.common.db.upsert
import io.beatmaps.common.db.wrapAsExpressionNotNull
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.ModLog
import io.beatmaps.common.dbo.Playlist
import io.beatmaps.common.dbo.PlaylistDao
import io.beatmaps.common.dbo.PlaylistMap
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.common.dbo.curatorAlias
import io.beatmaps.common.dbo.handleCurator
import io.beatmaps.common.dbo.handleOwner
import io.beatmaps.common.dbo.joinBookmarked
import io.beatmaps.common.dbo.joinCurator
import io.beatmaps.common.dbo.joinOwner
import io.beatmaps.common.dbo.joinPlaylistCurator
import io.beatmaps.common.dbo.joinUploader
import io.beatmaps.common.dbo.joinVersions
import io.beatmaps.common.localPlaylistCoverFolder
import io.beatmaps.common.pub
import io.beatmaps.controllers.UploadException
import io.beatmaps.controllers.reCaptchaVerify
import io.beatmaps.controllers.uploadDir
import io.beatmaps.login.Session
import io.beatmaps.util.cdnPrefix
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.locations.Location
import io.ktor.server.locations.get
import io.ktor.server.locations.options
import io.ktor.server.locations.post
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import net.coobird.thumbnailator.Thumbnails
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Coalesce
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.FieldSet
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.LiteralOp
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.avg
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.countDistinct
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.floatLiteral
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.postgresql.util.PSQLException
import org.valiktor.constraints.NotBlank
import org.valiktor.functions.hasSize
import org.valiktor.functions.isNotBlank
import org.valiktor.validate
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.Base64

const val prefix: String = "/playlists"
@Location("/api") class PlaylistApi {
    @Location("$prefix/id/{id}") data class Detail(val id: Int, val api: PlaylistApi)
    @Group("Playlists") @Location("$prefix/id/{id}/{page}") data class DetailWithPage(val id: Int, @DefaultValue("0") val page: Long, @Ignore val api: PlaylistApi)
    @Location("$prefix/id/{id}/download/{filename?}") data class Download(val id: Int, val filename: String? = null, val api: PlaylistApi)
    @Location("$prefix/id/{id}/add") data class Add(val id: Int, val api: PlaylistApi)
    @Group("Playlists") @Location("$prefix/id/{id}/batch") data class Batch(val id: Int, @Ignore val api: PlaylistApi)
    @Location("$prefix/id/{id}/edit") data class Edit(val id: Int, val api: PlaylistApi)
    @Location("$prefix/create") data class Create(val api: PlaylistApi)
    @Location("$prefix/curate") data class Curate(val api: PlaylistApi)
    @Group("Playlists") @Location("$prefix/user/{userId}/{page}") data class ByUser(
        val userId: Int,
        val page: Long,
        @Ignore
        val basic: Boolean = false,
        @Ignore
        val api: PlaylistApi
    )
    @Group("Playlists") @Location("$prefix/latest") data class ByUploadDate(
        @Description("You probably want this. Supplying the uploaded time of the last map in the previous page will get you another page.\nYYYY-MM-DDTHH:MM:SS+00:00")
        val before: Instant? = null,
        @Description("Like `before` but will get you maps more recent than the time supplied.\nYYYY-MM-DDTHH:MM:SS+00:00")
        val after: Instant? = null,
        val sort: LatestPlaylistSort? = LatestPlaylistSort.CREATED,
        @Ignore
        val api: PlaylistApi
    )
    @Group("Playlists") @Location("$prefix/search/{page}")
    data class Text(
        val q: String? = "",
        @DefaultValue("0") val page: Long = 0,
        val sortOrder: SearchOrder = SearchOrder.Relevance,
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
    UPDATED, SONGS_UPDATED, CREATED
}

data class MultipartRequest(val dataMap: Map<String, String>, val recaptchaSuccess: Boolean)

suspend fun ApplicationCall.handleMultipart(cb: suspend (PartData.FileItem) -> Unit): MultipartRequest {
    val multipart = receiveMultipart()
    val dataMap = mutableMapOf<String, String>()
    var recaptchaSuccess = false

    multipart.forEachPart { part ->
        if (part is PartData.FormItem) {
            // Process recaptcha immediately as it is time-critical
            if (part.name.toString() == "recaptcha") {
                recaptchaSuccess = (reCaptchaVerify == null) || run {
                    val verifyResponse = withContext(Dispatchers.IO) {
                        reCaptchaVerify.verify(part.value, request.origin.remoteHost)
                    }

                    verifyResponse.isSuccess || throw UploadException("Could not verify user [${verifyResponse.errorCodes.joinToString(", ")}]")
                }
            }

            dataMap[part.name.toString()] = part.value
        } else if (part is PartData.FileItem) {
            cb(part)
        }
    }

    return MultipartRequest(dataMap, recaptchaSuccess)
}

fun getMaxMapForUser(userId: Int) = Coalesce(
    wrapAsExpressionNotNull(
        PlaylistMap
            .join(User, JoinType.RIGHT, User.bookmarksId, PlaylistMap.playlistId)
            .slice(PlaylistMap.order.plus(1f))
            .select {
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
            .slice(PlaylistMap.order.plus(1f))
            .select {
                PlaylistMap.playlistId eq id
            }
            .orderBy(PlaylistMap.order, SortOrder.DESC)
            .limit(1),
        PlaylistMap.order.columnType
    ),
    floatLiteral(1f)
)

val playlistStats = listOf(
    Beatmap.uploader.countDistinct(),
    Beatmap.duration.sum(),
    Beatmap.upVotesInt.sum(),
    Beatmap.downVotesInt.sum(),
    Beatmap.score.avg(4)
)

fun Route.playlistRoute() {
    options<PlaylistApi.ByUploadDate> {
        call.response.header("Access-Control-Allow-Origin", "*")
        call.respond(HttpStatusCode.OK)
    }

    get<PlaylistApi.ByUploadDate>("Get playlists ordered by created/updated".responds(ok<PlaylistSearchResponse>())) {
        call.response.header("Access-Control-Allow-Origin", "*")

        optionalAuthorization(OauthScope.PLAYLISTS) { sess ->
            val sortField = when (it.sort) {
                null, LatestPlaylistSort.CREATED -> Playlist.createdAt
                LatestPlaylistSort.SONGS_UPDATED -> Playlist.songsChangedAt
                LatestPlaylistSort.UPDATED -> Playlist.updatedAt
            }

            val playlists = transaction {
                Playlist
                    .joinMaps()
                    .joinPlaylistCurator()
                    .joinOwner()
                    .slice(Playlist.columns + User.columns + curatorAlias.columns + playlistStats)
                    .select {
                        Playlist.id.inSubQuery(
                            Playlist
                                .slice(Playlist.id)
                                .select {
                                    (Playlist.deletedAt.isNull() and (sess?.let { s -> Playlist.owner eq s.userId or (Playlist.type eq EPlaylistType.Public) } ?: (Playlist.type eq EPlaylistType.Public)))
                                        .notNull(it.before) { o -> sortField less o.toJavaInstant() }
                                        .notNull(it.after) { o -> sortField greater o.toJavaInstant() }
                                }
                                .orderBy(sortField to (if (it.after != null) SortOrder.ASC else SortOrder.DESC))
                                .limit(20)
                        )
                    }
                    .groupBy(Playlist.id, User.id, curatorAlias[User.id])
                    .handleOwner()
                    .handleCurator()
                    .sortedByDescending { row ->
                        when (it.sort) {
                            null, LatestPlaylistSort.CREATED -> row[Playlist.createdAt]
                            LatestPlaylistSort.SONGS_UPDATED -> row[Playlist.songsChangedAt]
                            LatestPlaylistSort.UPDATED -> row[Playlist.updatedAt]
                        }
                    }
                    .map { playlist ->
                        PlaylistFull.from(playlist, cdnPrefix())
                    }
            }

            call.respond(PlaylistSearchResponse(playlists))
        }
    }

    options<PlaylistApi.Text> {
        call.response.header("Access-Control-Allow-Origin", "*")
        call.respond(HttpStatusCode.OK)
    }

    get<PlaylistApi.Text>("Search for playlists".responds(ok<PlaylistSearchResponse>())) {
        call.response.header("Access-Control-Allow-Origin", "*")

        val searchFields = PgConcat(" ", Playlist.name, Playlist.description)
        val searchInfo = parseSearchQuery(it.q, searchFields)
        val actualSortOrder = searchInfo.validateSearchOrder(it.sortOrder)
        val sortArgs = when (actualSortOrder) {
            SearchOrder.Relevance -> listOf(searchInfo.similarRank to SortOrder.DESC, Playlist.createdAt to SortOrder.DESC)
            SearchOrder.Rating, SearchOrder.Latest -> listOf(Playlist.createdAt to SortOrder.DESC)
            SearchOrder.Curated -> listOf(Playlist.curatedAt to SortOrder.DESC_NULLS_LAST, Playlist.createdAt to SortOrder.DESC)
        }.toTypedArray()

        newSuspendedTransaction {
            val playlists = Playlist
                .joinMaps()
                .joinOwner()
                .joinPlaylistCurator()
                .slice(
                    (if (actualSortOrder == SearchOrder.Relevance) listOf(searchInfo.similarRank) else listOf()) +
                        Playlist.columns + User.columns + curatorAlias.columns + playlistStats
                )
                .select {
                    Playlist.id.inSubQuery(
                        Playlist
                            .joinOwner()
                            .slice(Playlist.id)
                            .select {
                                (Playlist.deletedAt.isNull() and (Playlist.type eq EPlaylistType.Public))
                                    .let { q -> searchInfo.applyQuery(q) }
                                    .let { q ->
                                        if (it.includeEmpty != true) {
                                            q.and(Playlist.totalMaps greater 0)
                                        } else q
                                    }
                                    .notNull(searchInfo.userSubQuery) { o -> Playlist.owner inSubQuery o }
                                    .notNull(it.minNps) { o -> Playlist.maxNps greaterEqF o }
                                    .notNull(it.maxNps) { o -> Playlist.minNps lessEqF o }
                                    .notNull(it.from) { o -> Playlist.createdAt greaterEq o.toJavaInstant() }
                                    .notNull(it.to) { o -> Playlist.createdAt lessEq o.toJavaInstant() }
                                    .notNull(it.curated) { o -> with(Playlist.curatedAt) { if (o) isNotNull() else isNull() } }
                                    .notNull(it.verified) { o -> User.verifiedMapper eq o }
                            }
                            .orderBy(*sortArgs)
                            .limit(it.page)
                    )
                }
                .groupBy(Playlist.id, User.id, curatorAlias[User.id])
                .orderBy(*sortArgs)
                .handleCurator()
                .handleOwner()
                .map { playlist ->
                    PlaylistFull.from(playlist, cdnPrefix())
                }

            call.respond(PlaylistSearchResponse(playlists))
        }
    }

    fun getDetail(id: Int, cdnPrefix: String, userId: Int?, isAdmin: Boolean, page: Long?): PlaylistPage? {
        val detailPage = transaction {
            val playlist = Playlist
                .joinMaps()
                .joinOwner()
                .joinPlaylistCurator()
                .slice(Playlist.columns + User.columns + curatorAlias.columns + playlistStats)
                .select {
                    (Playlist.id eq id).let {
                        if (isAdmin) {
                            it
                        } else {
                            it and Playlist.deletedAt.isNull()
                        }
                    }
                }
                .groupBy(Playlist.id, User.id, curatorAlias[User.id])
                .handleOwner()
                .handleCurator()
                .firstOrNull()?.let {
                    PlaylistFull.from(it, cdnPrefix)
                }

            val mapsWithOrder = page?.let { page ->
                val mapsSubQuery = PlaylistMap
                    .join(Beatmap, JoinType.INNER, PlaylistMap.mapId, Beatmap.id)
                    .joinVersions()
                    .slice(PlaylistMap.mapId, PlaylistMap.order)
                    .select {
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
                    .joinCurator()
                    .joinBookmarked(userId)
                    .select {
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

            PlaylistPage(playlist, mapsWithOrder)
        }

        return if (detailPage.playlist != null && (detailPage.playlist.type == EPlaylistType.Public || detailPage.playlist.owner.id == userId || isAdmin)) {
            detailPage
        } else {
            null
        }
    }

    get<PlaylistApi.Detail> { req ->
        optionalAuthorization(OauthScope.PLAYLISTS) { sess ->
            getDetail(req.id, cdnPrefix(), sess?.userId, sess?.isAdmin() == true, null)?.let { call.respond(it) } ?: call.respond(HttpStatusCode.NotFound)
        }
    }

    options<PlaylistApi.DetailWithPage> {
        call.response.header("Access-Control-Allow-Origin", "*")
        call.respond(HttpStatusCode.OK)
    }

    get<PlaylistApi.DetailWithPage>("Get playlist detail".responds(ok<PlaylistPage>(), notFound())) { req ->
        call.response.header("Access-Control-Allow-Origin", "*")

        optionalAuthorization(OauthScope.PLAYLISTS) { sess ->
            getDetail(req.id, cdnPrefix(), sess?.userId, sess?.isAdmin() == true, req.page)?.let { call.respond(it) } ?: call.respond(HttpStatusCode.NotFound)
        }
    }

    options<PlaylistApi.ByUser> {
        call.response.header("Access-Control-Allow-Origin", "*")
        call.respond(HttpStatusCode.OK)
    }

    get<PlaylistApi.ByUser>("Get playlists by user".responds(ok<PlaylistSearchResponse>())) { req ->
        call.response.header("Access-Control-Allow-Origin", "*")

        optionalAuthorization(OauthScope.PLAYLISTS) { sess ->
            fun <T> doQuery(table: FieldSet = Playlist, groupBy: Array<Column<*>> = arrayOf(Playlist.id), block: (ResultRow) -> T) =
                transaction {
                    table
                        .select {
                            Playlist.id.inSubQuery(
                                Playlist
                                    .slice(Playlist.id)
                                    .select {
                                        ((Playlist.owner eq req.userId) and Playlist.deletedAt.isNull()).let {
                                            if (req.userId == sess?.userId) {
                                                it
                                            } else {
                                                it and (Playlist.type eq EPlaylistType.Public)
                                            }
                                        }
                                    }
                                    .orderBy(
                                        (Playlist.type neq EPlaylistType.System) to SortOrder.ASC,
                                        Playlist.createdAt to SortOrder.DESC
                                    )
                                    .limit(req.page, 20)
                            )
                        }
                        .orderBy(
                            (Playlist.type neq EPlaylistType.System) to SortOrder.ASC,
                            Playlist.createdAt to SortOrder.DESC
                        )
                        .groupBy(*groupBy)
                        .handleOwner()
                        .handleCurator()
                        .map(block)
                }

            if (req.basic) {
                val page = doQuery {
                    PlaylistBasic.from(it, cdnPrefix())
                }

                call.respond(page)
            } else {
                val page = doQuery(
                    Playlist
                        .joinMaps()
                        .joinOwner()
                        .joinPlaylistCurator()
                        .slice(Playlist.columns + User.columns + curatorAlias.columns + playlistStats),
                    arrayOf(Playlist.id, User.id, curatorAlias[User.id])
                ) {
                    PlaylistFull.from(it, cdnPrefix())
                }

                call.respond(PlaylistSearchResponse(page))
            }
        }
    }

    val bookmarksIcon = javaClass.classLoader.getResourceAsStream("assets/favicon/android-chrome-512x512.png")!!.readAllBytes()
    get<PlaylistApi.Download> { req ->
        val (playlist, playlistSongs) = transaction {
            fun getPlaylist() =
                Playlist
                    .joinPlaylistCurator()
                    .joinOwner()
                    .select {
                        (Playlist.id eq req.id) and Playlist.deletedAt.isNull()
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
                    .select {
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
            getPlaylist() to getMapsInPlaylist()
        }

        optionalAuthorization(OauthScope.PLAYLISTS) { sess ->
            if (playlist != null && (playlist.type == EPlaylistType.Public || playlist.owner.id == sess?.userId)) {
                val localFile = when (playlist.type) {
                    EPlaylistType.System -> bookmarksIcon
                    else -> File(localPlaylistCoverFolder(), "${playlist.playlistId}.jpg").readBytes()
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

    class PlaylistChangeException(msg: String) : Exception(msg)
    fun <T> catchNullRelation(block: () -> T) = try {
        block()
    } catch (e: ExposedSQLException) {
        val cause = e.cause
        if (cause is PSQLException && cause.sqlState == "23502") {
            // Set value is null. Map not found
            throw PlaylistChangeException("Cancel transaction")
        }

        throw e
    }

    fun applyPlaylistChange(pId: Int, inPlaylist: Boolean, mapId: Expression<EntityID<Int>>, newOrder: Float? = null) =
        catchNullRelation {
            if (inPlaylist) {
                PlaylistMap.upsert(conflictIndex = PlaylistMap.link) {
                    it[playlistId] = pId
                    it[PlaylistMap.mapId] = mapId
                    it[order] = newOrder?.let { f -> floatLiteral(f) } ?: getMaxMap(pId)
                }

                true
            } else {
                PlaylistMap.deleteWhere {
                    (PlaylistMap.playlistId eq pId) and (PlaylistMap.mapId eq mapId)
                }.let { res -> res > 0 }
            }
        }

    fun applyPlaylistChange(pId: Int, inPlaylist: Boolean, mapId: Int, newOrder: Float? = null) =
        applyPlaylistChange(pId, inPlaylist, LiteralOp(Beatmap.id.columnType, EntityID(mapId, Beatmap)), newOrder)

    post<PlaylistApi.Batch, PlaylistBatchRequest>("Add or remove up to 100 maps to a playlist. Requires OAUTH".responds(ok<ActionResponse>())) { req, pbr ->
        requireAuthorization(OauthScope.MANAGE_PLAYLISTS) { sess ->
            val validKeys = (pbr.keys ?: listOf()).mapNotNull { key -> key.toIntOrNull(16) }
            val hashesOrEmpty = pbr.hashes ?: listOf()
            if (hashesOrEmpty.size + validKeys.size > 100) {
                call.respond(HttpStatusCode.BadRequest, "Too many maps")
                return@requireAuthorization
            } else if (hashesOrEmpty.size + validKeys.size <= 0 || (validKeys.size != (pbr.keys?.size ?: 0) && pbr.ignoreUnknown != true)) {
                // No hashes or keys
                // OR
                // Some invalid keys but not allowed to ignore unknown
                call.respond(HttpStatusCode.BadRequest, "Nothing to do")
                return@requireAuthorization
            }

            try {
                transaction {
                    Playlist
                        .updateReturning(
                            {
                                (Playlist.id eq req.id) and (Playlist.owner eq sess.userId) and Playlist.deletedAt.isNull()
                            },
                            {
                                it[songsChangedAt] = NowExpression(songsChangedAt.columnType)
                            },
                            *Playlist.columns.toTypedArray()
                        )?.firstOrNull()?.let { row ->
                            val playlist = PlaylistDao.wrapRow(row)
                            val maxMap =
                                PlaylistMap
                                    .slice(PlaylistMap.order)
                                    .select {
                                        PlaylistMap.playlistId eq playlist.id.value
                                    }
                                    .orderBy(PlaylistMap.order, SortOrder.DESC)
                                    .limit(1)
                                    .firstOrNull()
                                    ?.let {
                                        it[PlaylistMap.order]
                                    } ?: 0f

                            val lookup = Beatmap
                                .joinVersions(false, null)
                                .slice(Versions.hash, Beatmap.id)
                                .select {
                                    Beatmap.deletedAt.isNull() and (Beatmap.id.inList(validKeys) or Versions.hash.inList(hashesOrEmpty))
                                }.associate {
                                    it[Versions.hash] to it[Beatmap.id].value
                                }
                            val unorderedMapids = lookup.values.toSet()

                            val mapIds = validKeys.filter { unorderedMapids.contains(it) } +
                                    hashesOrEmpty.mapNotNull { if (lookup.containsKey(it) && !validKeys.contains(lookup[it])) lookup[it] else null }

                            val result = if (mapIds.size != (hashesOrEmpty + validKeys).size && pbr.ignoreUnknown != true) {
                                rollback()
                                null
                            } else if (pbr.inPlaylist == true) {
                                PlaylistMap.batchInsert(mapIds.mapIndexed { idx, it -> idx to it }, true, shouldReturnGeneratedValues = false) {
                                    this[PlaylistMap.playlistId] = playlist.id
                                    this[PlaylistMap.mapId] = it.second
                                    this[PlaylistMap.order] = maxMap + it.first + 1
                                }.size
                            } else {
                                PlaylistMap.deleteWhere {
                                    (PlaylistMap.playlistId eq playlist.id.value) and (PlaylistMap.mapId.inList(mapIds))
                                }
                            }

                            result?.let {
                                if (it > 0) playlist.id.value else 0
                            }
                        }
                }
            } catch (_: PlaylistChangeException) {
                null
            }.let {
                when (it) {
                    null -> call.respond(HttpStatusCode.NotFound)
                    0 -> call.respond(ActionResponse(false))
                    else -> {
                        call.pub("beatmaps", "playlists.$it.updated", null, it)
                        call.respond(ActionResponse(true))
                    }
                }
            }
        }
    }

    post<PlaylistApi.Add> { req ->
        requireAuthorization(OauthScope.MANAGE_PLAYLISTS) { sess ->
            val pmr = call.receive<PlaylistMapRequest>()
            try {
                transaction {
                    Playlist
                        .updateReturning(
                            {
                                (Playlist.id eq req.id) and (Playlist.owner eq sess.userId) and Playlist.deletedAt.isNull()
                            },
                            {
                                it[songsChangedAt] = NowExpression(songsChangedAt.columnType)
                            },
                            *Playlist.columns.toTypedArray()
                        )?.firstOrNull()?.let { row ->
                            val playlist = PlaylistDao.wrapRow(row)
                            val newOrder = pmr.order

                            // Only perform these operations once we've verified the owner is logged in
                            // and the playlist exists (as above)
                            if (applyPlaylistChange(playlist.id.value, pmr.inPlaylist == true, pmr.mapId.toInt(16), newOrder)) {
                                playlist.id.value
                            } else {
                                0
                            }
                        }
                }
            } catch (_: PlaylistChangeException) {
                null
            } catch (_: NumberFormatException) {
                null
            }.let {
                when (it) {
                    null -> call.respond(HttpStatusCode.NotFound)
                    0 -> call.respond(ActionResponse(false))
                    else -> {
                        call.pub("beatmaps", "playlists.$it.updated", null, it)
                        call.respond(ActionResponse(true))
                    }
                }
            }
        }
    }

    fun typeFromReq(multipart: MultipartRequest, sess: Session) =
        EPlaylistType.fromString(multipart.dataMap["type"])?.let { newType ->
            if (sess.suspended || newType == EPlaylistType.System) null else newType
        } ?: EPlaylistType.Private

    val thumbnailSizes = listOf(256, 512)
    post<PlaylistApi.Create> {
        requireAuthorization(OauthScope.ADMIN_PLAYLISTS) { sess ->
            val files = mutableMapOf<Int, File>()

            try {
                val multipart = call.handleMultipart { part ->
                    part.streamProvider().use { its ->
                        val tmp = ByteArrayOutputStream()
                        its.copyToSuspend(tmp, sizeLimit = 10 * 1024 * 1024)

                        thumbnailSizes.forEach { s ->
                            files[s] = File(uploadDir, "upload-${System.currentTimeMillis()}-${sess.userId.hashCode()}-$s.jpg").also { localFile ->
                                Thumbnails
                                    .of(tmp.toByteArray().inputStream())
                                    .size(s, s)
                                    .outputFormat("JPEG")
                                    .outputQuality(0.8)
                                    .toFile(localFile)
                            }
                        }
                    }
                }

                multipart.recaptchaSuccess || throw UploadException("Missing recaptcha?")

                val toCreate = PlaylistBasic(
                    0,
                    "",
                    multipart.dataMap["name"] ?: "",
                    typeFromReq(multipart, sess),
                    sess.userId
                )

                validate(toCreate) {
                    validate(PlaylistBasic::name).isNotBlank().hasSize(3, 255)
                    validate(PlaylistBasic::playlistImage).validate(NotBlank) {
                        files.isNotEmpty()
                    }
                }

                val newId = transaction {
                    Playlist.insertAndGetId {
                        it[name] = toCreate.name
                        it[description] = multipart.dataMap["description"] ?: ""
                        it[owner] = toCreate.owner
                        it[type] = toCreate.type
                    }
                }

                files.forEach { (s, temp) ->
                    val localFile = File(localPlaylistCoverFolder(s), "$newId.jpg")
                    Files.move(temp.toPath(), localFile.toPath())
                }

                call.respond(newId.value)
            } finally {
                files.values.forEach { temp ->
                    temp.delete()
                }
            }
        }
    }

    post<PlaylistApi.Edit> { req ->
        requireAuthorization(OauthScope.ADMIN_PLAYLISTS) { sess ->
            val query = (Playlist.id eq req.id and Playlist.deletedAt.isNull()).let { q ->
                if (sess.isAdmin()) {
                    q
                } else {
                    q.and(Playlist.owner eq sess.userId)
                } and (Playlist.type neq EPlaylistType.System)
            }

            val beforePlaylist = transaction {
                Playlist.select(query).firstOrNull()?.let { PlaylistFull.from(it, cdnPrefix()) }
            } ?: throw UploadException("Playlist not found")

            val multipart = call.handleMultipart { part ->
                part.streamProvider().use { its ->
                    val tmp = ByteArrayOutputStream()
                    its.copyToSuspend(tmp, sizeLimit = 10 * 1024 * 1024)

                    thumbnailSizes.forEach { s ->
                        val localFile = File(localPlaylistCoverFolder(s), "${req.id}.jpg")

                        Thumbnails
                            .of(tmp.toByteArray().inputStream())
                            .size(s, s)
                            .outputFormat("JPEG")
                            .outputQuality(0.8)
                            .toFile(localFile)
                    }
                }
            }

            val shouldDelete = multipart.dataMap["deleted"].toBoolean()
            val newDescription = multipart.dataMap["description"] ?: ""
            val toCreate = PlaylistBasic(
                0, "",
                multipart.dataMap["name"] ?: "",
                typeFromReq(multipart, sess),
                sess.userId
            )

            if (!shouldDelete) {
                validate(toCreate) {
                    validate(PlaylistBasic::name).isNotBlank().hasSize(3, 255)
                }
            }

            transaction {
                fun updatePlaylist() {
                    Playlist.update({
                        query
                    }) {
                        if (shouldDelete) {
                            it[deletedAt] = NowExpression(deletedAt.columnType)
                        } else {
                            it[name] = toCreate.name
                            it[description] = newDescription
                            it[type] = toCreate.type
                        }
                        it[updatedAt] = NowExpression(updatedAt.columnType)
                    } > 0 || throw UploadException("Update failed")
                }

                updatePlaylist().also {
                    if (sess.isAdmin() && beforePlaylist.owner.id != sess.userId) {
                        ModLog.insert(
                            sess.userId,
                            null,
                            if (shouldDelete) {
                                DeletedPlaylistData(req.id, multipart.dataMap["reason"] ?: "")
                            } else {
                                EditPlaylistData(
                                    req.id,
                                    beforePlaylist.name, beforePlaylist.description, beforePlaylist.type == EPlaylistType.Public,
                                    toCreate.name, newDescription, toCreate.type == EPlaylistType.Public
                                )
                            },
                            beforePlaylist.owner.id
                        )
                    }
                }
            }

            call.respond(HttpStatusCode.OK)
        }
    }

    post<PlaylistApi.Curate> {
        requireAuthorization { user ->
            if (!user.isCurator()) {
                call.respond(HttpStatusCode.BadRequest)
            } else {
                val playlistUpdate = call.receive<CuratePlaylist>()

                val result = transaction {
                    Playlist.updateReturning(
                        {
                            (Playlist.id eq playlistUpdate.id) and (if (playlistUpdate.curated) Playlist.curatedAt.isNull() else Playlist.curatedAt.isNotNull()) and Playlist.deletedAt.isNull()
                        },
                        {
                            if (playlistUpdate.curated) {
                                it[curatedAt] = NowExpression(curatedAt.columnType)
                                it[curator] = EntityID(user.userId, User)
                            } else {
                                it[curatedAt] = null
                                it[curator] = null
                            }
                            it[updatedAt] = NowExpression(updatedAt.columnType)
                        },
                        *Playlist.columns.toTypedArray()
                    )?.firstOrNull()?.let {
                        PlaylistFull.from(it, cdnPrefix())
                    }
                }

                call.respond(result ?: HttpStatusCode.BadRequest)
            }
        }
    }
}
