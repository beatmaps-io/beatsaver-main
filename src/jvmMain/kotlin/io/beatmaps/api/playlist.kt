package io.beatmaps.api

import de.nielsfalk.ktor.swagger.DefaultValue
import de.nielsfalk.ktor.swagger.Description
import de.nielsfalk.ktor.swagger.Ignore
import de.nielsfalk.ktor.swagger.get
import de.nielsfalk.ktor.swagger.notFound
import de.nielsfalk.ktor.swagger.ok
import de.nielsfalk.ktor.swagger.responds
import de.nielsfalk.ktor.swagger.version.shared.Group
import io.beatmaps.cdnPrefix
import io.beatmaps.common.Config
import io.beatmaps.common.DeletedPlaylistData
import io.beatmaps.common.EditPlaylistData
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.cleanString
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.db.PgConcat
import io.beatmaps.common.db.updateReturning
import io.beatmaps.common.db.upsert
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Beatmap.joinCurator
import io.beatmaps.common.dbo.Beatmap.joinUploader
import io.beatmaps.common.dbo.Beatmap.joinVersions
import io.beatmaps.common.dbo.ModLog
import io.beatmaps.common.dbo.Playlist
import io.beatmaps.common.dbo.Playlist.joinOwner
import io.beatmaps.common.dbo.PlaylistDao
import io.beatmaps.common.dbo.PlaylistMap
import io.beatmaps.common.dbo.PlaylistMapDao
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.common.dbo.handleOwner
import io.beatmaps.common.localPlaylistCoverFolder
import io.beatmaps.controllers.UploadException
import io.beatmaps.controllers.reCaptchaVerify
import io.beatmaps.controllers.uploadDir
import io.beatmaps.login.Session
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.features.origin
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.locations.options
import io.ktor.locations.post
import io.ktor.request.receive
import io.ktor.request.receiveMultipart
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import net.coobird.thumbnailator.Thumbnails
import org.jetbrains.exposed.sql.Join
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.valiktor.constraints.NotBlank
import org.valiktor.functions.hasSize
import org.valiktor.functions.isNotBlank
import org.valiktor.validate
import java.io.File
import java.nio.file.Files

const val prefix: String = "/playlists"
@Location("/api") class PlaylistApi {
    @Location("$prefix/id/{id}") data class Detail(val id: Int, val api: PlaylistApi)
    @Group("Playlists") @Location("$prefix/id/{id}/{page}") data class DetailWithPage(val id: Int, @DefaultValue("0") val page: Long, @Ignore val api: PlaylistApi)
    @Location("$prefix/id/{id}/download/{filename?}") data class Download(val id: Int, val filename: String? = null, val api: PlaylistApi)
    @Location("$prefix/id/{id}/add") data class Add(val id: Int, val api: PlaylistApi)
    @Location("$prefix/id/{id}/edit") data class Edit(val id: Int, val api: PlaylistApi)
    @Location("$prefix/create") data class Create(val api: PlaylistApi)
    @Location("$prefix/user/{userId}/{page}") data class ByUser(val userId: Int, val page: Long, val api: PlaylistApi)
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
        val from: Instant? = null,
        val to: Instant? = null,
        val includeEmpty: Boolean? = null,
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

fun Route.playlistRoute() {
    options<PlaylistApi.ByUploadDate> {
        call.response.header("Access-Control-Allow-Origin", "*")
        call.respond(HttpStatusCode.OK)
    }
    get<PlaylistApi.ByUploadDate>("Get playlists ordered by created/updated".responds(ok<SearchResponse>())) {
        call.response.header("Access-Control-Allow-Origin", "*")

        val sess = call.sessions.get<Session>()
        val sortField = when (it.sort) {
            null, LatestPlaylistSort.CREATED -> Playlist.createdAt
            LatestPlaylistSort.SONGS_UPDATED -> Playlist.songsChangedAt
            LatestPlaylistSort.UPDATED -> Playlist.updatedAt
        }

        val playlists = transaction {
            Playlist
                .joinOwner()
                .select {
                    (Playlist.deletedAt.isNull() and (sess?.let { s -> Playlist.owner eq s.userId or Playlist.public } ?: Playlist.public))
                        .notNull(it.before) { o -> sortField less o.toJavaInstant() }
                        .notNull(it.after) { o -> sortField greater o.toJavaInstant() }
                }
                .orderBy(sortField to (if (it.after != null) SortOrder.ASC else SortOrder.DESC))
                .limit(20)
                .handleOwner()
                .map { row -> PlaylistDao.wrapRow(row) }
                .sortedByDescending { map ->
                    when (it.sort) {
                        null, LatestPlaylistSort.CREATED -> map.createdAt
                        LatestPlaylistSort.SONGS_UPDATED -> map.songsChangedAt
                        LatestPlaylistSort.UPDATED -> map.updatedAt
                    }
                }
                .map { playlist ->
                    PlaylistFull.from(playlist, cdnPrefix())
                }
        }

        call.respond(PlaylistSearchResponse(playlists))
    }

    options<PlaylistApi.Text> {
        call.response.header("Access-Control-Allow-Origin", "*")
        call.respond(HttpStatusCode.OK)
    }

    get<PlaylistApi.Text>("Search for playlists".responds(ok<SearchResponse>())) {
        call.response.header("Access-Control-Allow-Origin", "*")

        val searchFields = PgConcat(" ", Playlist.name, Playlist.description)
        val searchInfo = parseSearchQuery(it.q, searchFields)
        val actualSortOrder = searchInfo.validateSearchOrder(it.sortOrder)

        newSuspendedTransaction {
            val playlists = Playlist
                .let { q ->
                    if (it.includeEmpty != true) {
                        q.joinMaps(type = JoinType.INNER)
                    } else {
                        q
                    }
                }
                .joinOwner()
                .slice((if (actualSortOrder == SearchOrder.Relevance) listOf(searchInfo.similarRank) else listOf()) + Playlist.columns + User.columns)
                .select {
                    (Playlist.deletedAt.isNull() and Playlist.public)
                        .let { q -> searchInfo.applyQuery(q) }
                        .notNull(searchInfo.userSubQuery) { o -> Playlist.owner inSubQuery o }
                        .notNull(it.from) { o -> Beatmap.uploaded greaterEq o.toJavaInstant() }
                        .notNull(it.to) { o -> Beatmap.uploaded lessEq o.toJavaInstant() }
                }
                .groupBy(Playlist.id, User.id)
                .orderBy(
                    when (actualSortOrder) {
                        SearchOrder.Relevance -> searchInfo.similarRank
                        SearchOrder.Rating, SearchOrder.Latest -> Playlist.createdAt
                    },
                    SortOrder.DESC
                )
                .limit(it.page)
                .map { playlist ->
                    PlaylistFull.from(playlist, cdnPrefix())
                }

            call.respond(PlaylistSearchResponse(playlists))
        }
    }

    fun getDetail(id: Int, cdnPrefix: String, userId: Int?, isAdmin: Boolean, page: Long?): PlaylistPage? {
        val detailPage = transaction {
            val playlist = Playlist
                .joinOwner()
                .select {
                    (Playlist.id eq id).let {
                        if (isAdmin) {
                            it
                        } else {
                            it and Playlist.deletedAt.isNull()
                        }
                    }
                }
                .handleOwner()
                .firstOrNull()?.let {
                    PlaylistFull.from(it, cdnPrefix)
                }

            val mapsWithOrder = page?.let { page ->
                val mapsSubQuery = PlaylistMap
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

        return if (detailPage.playlist != null && (detailPage.playlist.public || detailPage.playlist.owner?.id == userId || isAdmin)) {
            detailPage
        } else {
            null
        }
    }

    get<PlaylistApi.Detail> { req ->
        val sess = call.sessions.get<Session>()
        getDetail(req.id, cdnPrefix(), sess?.userId, sess?.isAdmin() == true, null)?.let { call.respond(it) } ?: call.respond(HttpStatusCode.NotFound)
    }

    get<PlaylistApi.DetailWithPage>("Get playlist detail".responds(ok<PlaylistPage>(), notFound())) { req ->
        val sess = call.sessions.get<Session>()
        getDetail(req.id, cdnPrefix(), sess?.userId, sess?.isAdmin() == true, req.page)?.let { call.respond(it) } ?: call.respond(HttpStatusCode.NotFound)
    }

    get<PlaylistApi.ByUser> { req ->
        val sess = call.sessions.get<Session>()
        val page = transaction {
            Playlist
                .select {
                    ((Playlist.owner eq req.userId) and Playlist.deletedAt.isNull()).let {
                        if (req.userId == sess?.userId) {
                            it
                        } else {
                            it.and(Playlist.public)
                        }
                    }
                }
                .orderBy(Playlist.createdAt, SortOrder.DESC)
                .limit(req.page, 100)
                .map {
                    PlaylistBasic.from(it, cdnPrefix())
                }
        }

        call.respond(page)
    }

    get<PlaylistApi.Download> { req ->
        val (playlist, playlistSongs) = transaction {
            fun getPlaylist() =
                Playlist
                    .joinOwner()
                    .select {
                        (Playlist.id eq req.id) and Playlist.deletedAt.isNull()
                    }
                    .handleOwner()
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

        if (playlist != null && (playlist.public || playlist.owner?.id == call.sessions.get<Session>()?.userId)) {
            val cleanName = cleanString("BeatSaver - ${playlist.name}.bplist")
            call.response.headers.append(HttpHeaders.ContentDisposition, "attachment; filename=\"${cleanName}\"")
            call.respond(
                Playlist(
                    playlist.name,
                    playlist.owner?.name ?: "",
                    playlist.description,
                    "",
                    PlaylistCustomData("${Config.apiremotebase}/playlists/id/${playlist.playlistId}/download"),
                    playlistSongs
                )
            )
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    post<PlaylistApi.Add> { req ->
        requireAuthorization { sess ->
            val pmr = call.receive<PlaylistMapRequest>()
            try {
                transaction {
                    fun getMaxMap() = PlaylistMap
                        .select {
                            PlaylistMap.playlistId eq req.id
                        }
                        .orderBy(PlaylistMap.order, SortOrder.DESC)
                        .limit(1)
                        .firstOrNull()?.let { PlaylistMapDao.wrapRow(it) }

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
                            val newOrder = pmr.order ?: getMaxMap()?.let { it.order + 1 } ?: 1.0f

                            // Only perform these operations once we've verified the owner is logged in
                            // and the playlist exists (as above)
                            if (pmr.inPlaylist == true) {
                                // Add to playlist
                                PlaylistMap.upsert(conflictIndex = PlaylistMap.link) {
                                    it[playlistId] = playlist.id
                                    it[mapId] = pmr.mapId.toInt(16)
                                    it[order] = newOrder
                                }

                                true
                            } else {
                                PlaylistMap.deleteWhere {
                                    (PlaylistMap.playlistId eq req.id) and (PlaylistMap.mapId eq pmr.mapId.toInt(16))
                                } > 0
                            }
                        }
                }
            } catch (_: NumberFormatException) {
                null
            }.let {
                when (it) {
                    null -> call.respond(HttpStatusCode.NotFound)
                    else -> call.respond(ActionResponse(it))
                }
            }
        }
    }

    post<PlaylistApi.Create> {
        requireAuthorization { sess ->
            val temp = File(
                uploadDir,
                "upload-${System.currentTimeMillis()}-${sess.userId.hashCode()}.jpg"
            )
            try {
                val multipart = call.handleMultipart { part ->
                    part.streamProvider().use { its ->
                        Thumbnails
                            .of(its)
                            .size(256, 256)
                            .outputFormat("JPEG")
                            .outputQuality(0.8)
                            .toFile(temp)
                    }
                }

                multipart.recaptchaSuccess || throw UploadException("Missing recaptcha?")

                val toCreate = PlaylistBasic(
                    0,
                    "",
                    multipart.dataMap["name"] ?: "",
                    multipart.dataMap["public"].toBoolean(),
                    sess.userId
                )

                validate(toCreate) {
                    validate(PlaylistBasic::name).isNotBlank().hasSize(3, 255)
                    validate(PlaylistBasic::playlistImage).validate(NotBlank) {
                        temp.exists()
                    }
                }

                val newId = transaction {
                    Playlist.insertAndGetId {
                        it[name] = toCreate.name
                        it[description] = multipart.dataMap["description"] ?: ""
                        it[owner] = toCreate.owner
                        it[public] = toCreate.public
                    }
                }

                val localFile = File(localPlaylistCoverFolder(), "$newId.jpg")
                Files.move(temp.toPath(), localFile.toPath())

                call.respond(newId.value)
            } finally {
                temp.delete()
            }
        }
    }

    post<PlaylistApi.Edit> { req ->
        requireAuthorization { sess ->
            val localFile = File(localPlaylistCoverFolder(), "${req.id}.jpg")

            val query = (Playlist.id eq req.id and Playlist.deletedAt.isNull()).let { q ->
                if (sess.isAdmin()) {
                    q
                } else {
                    q.and(Playlist.owner eq sess.userId)
                }
            }

            val beforePlaylist = transaction {
                Playlist.select(query).firstOrNull()?.let { PlaylistFull.from(it, cdnPrefix()) }
            } ?: throw UploadException("Playlist not found")

            val multipart = call.handleMultipart { part ->
                part.streamProvider().use { its ->
                    Thumbnails
                        .of(its)
                        .size(256, 256)
                        .outputFormat("JPEG")
                        .outputQuality(0.8)
                        .toFile(localFile)
                }
            }

            val shouldDelete = multipart.dataMap["deleted"].toBoolean()
            val newDescription = multipart.dataMap["description"] ?: ""
            val toCreate = PlaylistBasic(
                0, "",
                multipart.dataMap["name"] ?: "",
                multipart.dataMap["public"].toBoolean(),
                sess.userId
            )

            if (!shouldDelete) {
                validate(toCreate) {
                    validate(PlaylistBasic::name).isNotBlank().hasSize(3, 255)
                }
            }

            transaction {
                fun updatePlaylist() =
                    Playlist.update({
                        query
                    }) {
                        if (shouldDelete) {
                            it[deletedAt] = NowExpression(deletedAt.columnType)
                        } else {
                            it[name] = toCreate.name
                            it[description] = newDescription
                            it[public] = toCreate.public
                        }
                        it[updatedAt] = NowExpression(updatedAt.columnType)
                    } > 0 || throw UploadException("Update failed")

                updatePlaylist().also {
                    if (it && sess.isAdmin() && beforePlaylist.owner?.id != sess.userId) {
                        ModLog.insert(
                            sess.userId,
                            null,
                            if (shouldDelete) {
                                DeletedPlaylistData(req.id,"")
                            } else {
                                EditPlaylistData(req.id, beforePlaylist.name, beforePlaylist.description, beforePlaylist.public, toCreate.name, newDescription, toCreate.public)
                            },
                            beforePlaylist.owner?.id ?: 0
                        )
                    }
                }
            }

            call.respond(HttpStatusCode.OK)
        }
    }
}
