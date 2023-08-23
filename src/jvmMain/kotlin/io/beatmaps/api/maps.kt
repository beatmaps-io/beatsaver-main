package io.beatmaps.api

import de.nielsfalk.ktor.swagger.DefaultValue
import de.nielsfalk.ktor.swagger.Description
import de.nielsfalk.ktor.swagger.Ignore
import de.nielsfalk.ktor.swagger.get
import de.nielsfalk.ktor.swagger.notFound
import de.nielsfalk.ktor.swagger.ok
import de.nielsfalk.ktor.swagger.responds
import de.nielsfalk.ktor.swagger.version.shared.Group
import io.beatmaps.common.DeletedData
import io.beatmaps.common.InfoEditData
import io.beatmaps.common.MapTag
import io.beatmaps.common.api.EAlertType
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.dbo.Alert
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.BeatmapDao
import io.beatmaps.common.dbo.Collaboration
import io.beatmaps.common.dbo.ModLog
import io.beatmaps.common.dbo.Playlist
import io.beatmaps.common.dbo.PlaylistDao
import io.beatmaps.common.dbo.PlaylistMap
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.common.dbo.joinBookmarked
import io.beatmaps.common.dbo.joinCollaborators
import io.beatmaps.common.dbo.joinCurator
import io.beatmaps.common.dbo.joinUploader
import io.beatmaps.common.dbo.joinVersions
import io.beatmaps.common.pub
import io.beatmaps.login.Session
import io.beatmaps.util.cdnPrefix
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.locations.Location
import io.ktor.server.locations.get
import io.ktor.server.locations.options
import io.ktor.server.locations.post
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.unionAll
import org.jetbrains.exposed.sql.update
import java.lang.Integer.toHexString

@Location("/api")
class MapsApi {
    @Location("/maps/update")
    data class Update(val api: MapsApi)

    @Location("/maps/tagupdate")
    data class TagUpdate(val api: MapsApi)

    @Location("/maps/curate")
    data class Curate(val api: MapsApi)

    @Location("/maps/validate")
    data class Validate(val api: MapsApi)

    @Location("/maps/wip/{page}")
    data class WIP(val page: Long = 0, val api: MapsApi)

    @Location("/download/key/{key}")
    data class BeatsaverDownload(val key: String, val api: MapsApi)

    @Location("/maps/beatsaver/{key}")
    data class Beatsaver(val key: String, @Ignore val api: MapsApi)

    @Group("Maps")
    @Location("/maps/id/{id}")
    data class Detail(val id: String, @Ignore val api: MapsApi)

    @Location("/maps/id/{id}/playlists")
    data class InPlaylists(val id: String, @Ignore val api: MapsApi)

    @Group("Maps")
    @Location("/maps/hash/{hash}")
    data class ByHash(
        @Description("Up to 50 hashes seperated by commas")
        val hash: String,
        @Ignore
        val api: MapsApi
    )

    @Group("Maps")
    @Location("/maps/uploader/{id}/{page}")
    data class ByUploader(val id: Int, @DefaultValue("0") val page: Long = 0, @Ignore val api: MapsApi)

    @Group("Maps")
    @Location("/maps/collaborations/{id}")
    data class Collaborations(val id: Int, val before: Instant? = null, @DefaultValue("20") @Description("1 - 100") val pageSize: Int? = 20, @Ignore val api: MapsApi)

    @Group("Maps")
    @Location("/maps/latest")
    data class ByUploadDate(
        @Description("You probably want this. Supplying the uploaded time of the last map in the previous page will get you another page.\nYYYY-MM-DDTHH:MM:SS+00:00")
        val before: Instant? = null,
        @Description("Like `before` but will get you maps more recent than the time supplied.\nYYYY-MM-DDTHH:MM:SS+00:00")
        val after: Instant? = null,
        @Description("true = both, false = no ai")
        val automapper: Boolean? = false,
        val sort: LatestSort? = LatestSort.FIRST_PUBLISHED,
        @Description("1 - 100") @DefaultValue("20")
        val pageSize: Int? = 20,
        @Ignore
        val api: MapsApi
    )

    @Group("Maps")
    @Location("/maps/plays/{page}")
    data class ByPlayCount(@DefaultValue("0") val page: Long = 0, @Ignore val api: MapsApi)

    @Group("Users")
    @Location("/users/id/{id}")
    data class UserId(val id: Int, @Ignore val api: MapsApi)

    @Group("Users")
    @Location("/users/name/{name}")
    data class UserName(val name: String, @Ignore val api: MapsApi)

    @Group("Users")
    @Location("/users/verify")
    data class Verify(@Ignore val api: MapsApi)
}

enum class LatestSort {
    FIRST_PUBLISHED, UPDATED, LAST_PUBLISHED, CREATED, CURATED
}

fun Route.mapDetailRoute() {
    options<MapsApi.Beatsaver> {
        call.response.header("Access-Control-Allow-Origin", "*")
        call.respond(HttpStatusCode.OK)
    }
    options<MapsApi.Detail> {
        call.response.header("Access-Control-Allow-Origin", "*")
        call.respond(HttpStatusCode.OK)
    }
    options<MapsApi.ByHash> {
        call.response.header("Access-Control-Allow-Origin", "*")
        call.respond(HttpStatusCode.OK)
    }
    options<MapsApi.ByUploader> {
        call.response.header("Access-Control-Allow-Origin", "*")
        call.respond(HttpStatusCode.OK)
    }
    options<MapsApi.ByUploadDate> {
        call.response.header("Access-Control-Allow-Origin", "*")
        call.respond(HttpStatusCode.OK)
    }
    options<MapsApi.ByPlayCount> {
        call.response.header("Access-Control-Allow-Origin", "*")
        call.respond(HttpStatusCode.OK)
    }

    post<MapsApi.Curate> {
        call.response.header("Access-Control-Allow-Origin", "*")
        requireAuthorization { user ->
            if (!user.isCurator()) {
                call.respond(HttpStatusCode.BadRequest)
            } else {
                val mapUpdate = call.receive<CurateMap>()

                val result = transaction {
                    fun curateMap() =
                        Beatmap.update({
                            (Beatmap.id eq mapUpdate.id) and (Beatmap.uploader neq user.userId) and (if (mapUpdate.curated) Beatmap.curatedAt.isNull() else Beatmap.curatedAt.isNotNull())
                        }) {
                            if (mapUpdate.curated) {
                                it[curatedAt] = NowExpression(curatedAt.columnType)
                                it[curator] = EntityID(user.userId, User)
                            } else {
                                it[curatedAt] = null
                                it[curator] = null
                            }
                            it[updatedAt] = NowExpression(updatedAt.columnType)
                        }

                    (curateMap() > 0).also { success ->
                        if (success) {
                            Beatmap.select {
                                Beatmap.id eq mapUpdate.id
                            }.complexToBeatmap().single().let {
                                if (mapUpdate.curated) {
                                    Alert.insert(
                                        "Your map has been curated",
                                        "@${user.uniqueName} just curated your map #${toHexString(mapUpdate.id)}: **${it.name}**.\n" +
                                            "Congratulations!",
                                        EAlertType.Curation,
                                        it.uploader.id.value
                                    )
                                } else {
                                    Alert.insert(
                                        "Your map has been uncurated",
                                        "@${user.uniqueName} just uncurated your map #${toHexString(mapUpdate.id)}: **${it.name}**.\n" +
                                            "Reason: *\"${mapUpdate.reason ?: ""}\"*",
                                        EAlertType.Uncuration,
                                        it.uploader.id.value
                                    )
                                }
                            }
                        }
                    }
                }

                if (result) call.pub("beatmaps", "maps.${mapUpdate.id}.updated.curation", null, mapUpdate.id)
                call.respond(if (result) HttpStatusCode.OK else HttpStatusCode.BadRequest)
            }
        }
    }

    post<MapsApi.Validate> {
        call.response.header("Access-Control-Allow-Origin", "*")
        requireAuthorization { user ->
            if (!user.isAdmin()) {
                call.respond(HttpStatusCode.BadRequest)
            } else {
                val mapUpdate = call.receive<ValidateMap>()

                val result = transaction {
                    Beatmap.update({
                        (Beatmap.id eq mapUpdate.id)
                    }) {
                        it[automapper] = mapUpdate.automapper
                        it[updatedAt] = NowExpression(updatedAt.columnType)
                    } > 0
                }

                if (result) call.pub("beatmaps", "maps.${mapUpdate.id}.updated.ai", null, mapUpdate.id)
                call.respond(if (result) HttpStatusCode.OK else HttpStatusCode.BadRequest)
            }
        }
    }

    post<MapsApi.Update> {
        call.response.header("Access-Control-Allow-Origin", "*")
        requireAuthorization { user ->
            val mapUpdate = call.receive<MapInfoUpdate>()

            val tooMany = mapUpdate.tags?.groupBy { it.type }?.mapValues { it.value.size }?.withDefault { 0 }?.let { byType ->
                MapTag.maxPerType.any { byType.getValue(it.key) > it.value }
            }

            val result = transaction {
                val oldData = if (user.isAdmin()) {
                    BeatmapDao.wrapRow(Beatmap.select { Beatmap.id eq mapUpdate.id }.single())
                } else {
                    null
                }

                fun updateMap() =
                    Beatmap.update({
                        (Beatmap.id eq mapUpdate.id and Beatmap.deletedAt.isNull()).let { q ->
                            if (user.isAdmin()) {
                                q // If current user is admin don't check the user
                            } else {
                                q and (Beatmap.uploader eq user.userId)
                            }
                        }
                    }) {
                        if (mapUpdate.deleted) {
                            it[deletedAt] = NowExpression(deletedAt.columnType)
                        } else {
                            mapUpdate.name?.let { n -> it[name] = n.take(1000) }
                            mapUpdate.description?.let { d -> it[description] = d.take(10000) }
                            if (tooMany != true) { // Don't update tags if request is trying to add too many tags
                                mapUpdate.tags?.filter { t -> t != MapTag.None }?.map { t -> t.slug }?.let { t -> it[tags] = t.toTypedArray() }
                            }
                            it[updatedAt] = NowExpression(updatedAt.columnType)
                        }
                    }

                (updateMap() > 0).also { rTemp ->
                    if (rTemp && oldData != null && oldData.uploaderId.value != user.userId) {
                        ModLog.insert(
                            user.userId,
                            mapUpdate.id,
                            if (mapUpdate.deleted) {
                                DeletedData(mapUpdate.reason ?: "")
                            } else {
                                InfoEditData(oldData.name, oldData.description, mapUpdate.name ?: "", mapUpdate.description ?: "", oldData.tags?.mapNotNull { MapTag.fromSlug(it) }, mapUpdate.tags)
                            },
                            oldData.uploaderId.value
                        )
                        if (mapUpdate.deleted) {
                            Alert.insert(
                                "Removal Notice",
                                "Your map #${toHexString(mapUpdate.id)}: **${oldData.name}** has been removed by a moderator.\n" +
                                    "Reason: *\"${mapUpdate.reason}\"*",
                                EAlertType.Deletion,
                                oldData.uploaderId.value
                            )
                        }
                    }
                }
            }

            val updateType = if (mapUpdate.deleted) "delete" else "info"
            if (result) call.pub("beatmaps", "maps.${mapUpdate.id}.updated.$updateType", null, mapUpdate.id)
            call.respond(if (result) HttpStatusCode.OK else HttpStatusCode.BadRequest)
        }
    }

    post<MapsApi.TagUpdate> {
        call.response.header("Access-Control-Allow-Origin", "*")
        requireAuthorization { user ->
            val mapUpdate = call.receive<SimpleMapInfoUpdate>()

            val tooMany = mapUpdate.tags?.groupBy { it.type }?.mapValues { it.value.size }?.withDefault { 0 }?.let { byType ->
                MapTag.maxPerType.any { byType.getValue(it.key) > it.value }
            }

            val result = if (tooMany != true && user.isCurator()) {
                transaction {
                    val oldData = BeatmapDao.wrapRow(Beatmap.select { Beatmap.id eq mapUpdate.id }.single())

                    fun updateMap() =
                        Beatmap.update({
                            Beatmap.id eq mapUpdate.id and Beatmap.deletedAt.isNull()
                        }) {
                            mapUpdate.tags?.filter { t -> t != MapTag.None }?.map { t -> t.slug }?.let { t -> it[tags] = t.toTypedArray() }
                            it[updatedAt] = NowExpression(updatedAt.columnType)
                        }

                    (updateMap() > 0).also { rTemp ->
                        if (rTemp && oldData.uploaderId.value != user.userId) {
                            ModLog.insert(
                                user.userId,
                                mapUpdate.id,
                                InfoEditData(oldData.name, oldData.description, "", "", oldData.tags?.mapNotNull { MapTag.fromSlug(it) }, mapUpdate.tags),
                                oldData.uploader.id.value
                            )
                        }
                    }
                }
            } else {
                false
            }

            if (result) call.pub("beatmaps", "maps.${mapUpdate.id}.updated.info", null, mapUpdate.id)
            call.respond(if (result) HttpStatusCode.OK else HttpStatusCode.BadRequest)
        }
    }

    get<MapsApi.Detail>("Get map information".responds(ok<MapDetail>(), notFound())) {
        call.response.header("Access-Control-Allow-Origin", "*")
        val sess = call.sessions.get<Session>()
        val isAdmin = sess?.isAdmin() == true
        val r = try {
            transaction {
                Beatmap
                    .joinVersions(true, state = null) // Allow returning non-published versions
                    .joinUploader()
                    .joinCurator()
                    .joinBookmarked(sess?.userId)
                    .joinCollaborators()
                    .select {
                        (Beatmap.id eq it.id.toInt(16)).let {
                            if (isAdmin) {
                                it
                            } else {
                                it and Beatmap.deletedAt.isNull()
                            }
                        }
                    }
                    .complexToBeatmap()
                    .firstOrNull()
                    ?.enrichTestplays()
                    ?.run {
                        MapDetail.from(this, cdnPrefix())
                    }
            }
        } catch (_: NumberFormatException) {
            null
        }

        if (r != null && (r.publishedVersion() != null || r.uploader.id == sess?.userId || sess?.testplay == true || isAdmin)) {
            call.respond(r)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    get<MapsApi.InPlaylists> {
        val mapId = it.id

        requireAuthorization {
            try {
                transaction {
                    Playlist.joinMaps {
                        PlaylistMap.mapId eq mapId.toInt(16)
                    }.select {
                        Playlist.owner eq it.userId and Playlist.deletedAt.isNull()
                    }.orderBy(Playlist.createdAt, SortOrder.DESC).map { row ->
                        PlaylistDao.wrapRow(row) to (row.getOrNull(PlaylistMap.id) != null)
                    }
                }.map { pmd -> InPlaylist(PlaylistBasic.from(pmd.first, cdnPrefix()), pmd.second) }
            } catch (_: NumberFormatException) {
                null
            }.let { inPlaylists ->
                when (inPlaylists) {
                    null -> call.respond(HttpStatusCode.NotFound)
                    else -> call.respond(inPlaylists)
                }
            }
        }
    }

    get<MapsApi.Beatsaver> {
        call.response.header("Access-Control-Allow-Origin", "*")
        val r = transaction {
            Beatmap
                .joinVersions(true)
                .joinUploader()
                .joinCurator()
                .select {
                    Beatmap.id.inSubQuery(
                        Versions
                            .slice(Versions.mapId)
                            .select {
                                Versions.key64 eq it.key
                            }
                            .limit(1)
                    ) and (Beatmap.deletedAt.isNull())
                }
                .complexToBeatmap()
                .firstOrNull()
                ?.run {
                    MapDetail.from(this, cdnPrefix())
                }
        }

        if (r == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respond(r)
        }
    }

    get<MapsApi.BeatsaverDownload> { k ->
        val r = try {
            transaction {
                Beatmap
                    .joinVersions(true)
                    .select {
                        Beatmap.id eq k.key.toInt(16) and (Beatmap.deletedAt.isNull())
                    }
                    .complexToBeatmap()
                    .firstOrNull()
                    ?.run {
                        MapDetail.from(this, cdnPrefix())
                    }
            }?.publishedVersion()
        } catch (_: NumberFormatException) {
            null
        }

        if (r == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respondRedirect(r.downloadURL)
        }
    }

    get<MapsApi.ByHash>("Get map(s) for a map hash".responds(ok<MapDetail>(), notFound())) {
        call.response.header("Access-Control-Allow-Origin", "*")
        val r = transaction {
            val versions = Versions
                .slice(Versions.mapId, Versions.hash)
                .select {
                    Versions.hash.inList(it.hash.lowercase().split(',', ignoreCase = false).take(50))
                }
            val versionMapping = versions.associate { it[Versions.hash] to it[Versions.mapId].value }
            val mapIds = versionMapping.values.toHashSet()

            Beatmap
                .joinVersions(true)
                .joinUploader()
                .joinCurator()
                .select {
                    Beatmap.id.inList(mapIds) and (Beatmap.deletedAt.isNull())
                }
                .complexToBeatmap()
                .map {
                    MapDetail.from(it, cdnPrefix())
                }.let { maps ->
                    val assocMaps = maps.associateBy { it.id }
                    when (maps.size) {
                        0 -> null
                        1 -> maps.first()
                        else -> {
                            versionMapping.mapValues {
                                assocMaps[toHexString(it.value)]
                            }
                        }
                    }
                }
        }

        if (r == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respond(r)
        }
    }

    get<MapsApi.WIP> { r ->
        requireAuthorization { sess ->
            val beatmaps = transaction {
                Beatmap
                    .joinVersions(true, state = null)
                    .joinUploader()
                    .joinCurator()
                    .joinBookmarked(sess.userId)
                    .select {
                        Beatmap.id.inSubQuery(
                            Beatmap
                                .join(
                                    Versions,
                                    JoinType.LEFT,
                                    onColumn = Beatmap.id,
                                    otherColumn = Versions.mapId,
                                    additionalConstraint = { Versions.state eq EMapState.Published }
                                )
                                .slice(Beatmap.id)
                                .select {
                                    Beatmap.uploader.eq(sess.userId) and Beatmap.deletedAt.isNull() and Versions.mapId.isNull()
                                }
                                .groupBy(Beatmap.id)
                                .orderBy(Beatmap.uploaded to SortOrder.DESC)
                                .limit(r.page)
                        )
                    }
                    .complexToBeatmap()
                    .map { map ->
                        MapDetail.from(map, cdnPrefix())
                    }
                    .sortedByDescending { it.uploaded }
            }

            call.respond(SearchResponse(beatmaps))
        }
    }

    get<MapsApi.ByUploader>("Get maps by a user".responds(ok<SearchResponse>())) {
        call.response.header("Access-Control-Allow-Origin", "*")

        val sess = call.sessions.get<Session>()
        val beatmaps = transaction {
            Beatmap
                .joinVersions(true)
                .joinUploader()
                .joinCurator()
                .joinBookmarked(sess?.userId)
                .select {
                    Beatmap.id.inSubQuery(
                        Beatmap
                            .joinVersions()
                            .slice(Beatmap.id)
                            .select {
                                Beatmap.uploader.eq(it.id) and (Beatmap.deletedAt.isNull())
                            }
                            .orderBy(Beatmap.uploaded to SortOrder.DESC)
                            .limit(it.page)
                    )
                }
                .complexToBeatmap()
                .map { map ->
                    MapDetail.from(map, cdnPrefix())
                }
                .sortedByDescending { it.uploaded }
        }

        call.respond(SearchResponse(beatmaps))
    }

    get<MapsApi.Collaborations>("Get maps by a user, including collaborations".responds(ok<SearchResponse>())) {
        call.response.header("Access-Control-Allow-Origin", "*")

        val sess = call.sessions.get<Session>()
        val pageSize = (it.pageSize ?: 20).coerceIn(1, 100)
        val beatmaps = transaction {
            val collabQuery = Collaboration
                .joinVersions(column = Collaboration.mapId)
                .slice(Collaboration.mapId, Collaboration.uploadedAt)
                .select {
                    (Collaboration.collaboratorId eq it.id and Collaboration.accepted)
                        .notNull(it.before) { o -> Collaboration.uploadedAt less o.toJavaInstant() }
                }
                .orderBy(Collaboration.uploadedAt to SortOrder.DESC)
                .limit(pageSize)

            val uploadQuery = Beatmap
                .joinVersions()
                .slice(Beatmap.id, Beatmap.uploaded)
                .select {
                    (Beatmap.uploader eq it.id and Beatmap.deletedAt.isNull())
                        .notNull(it.before) { o -> Beatmap.uploaded less o.toJavaInstant() }
                }
                .orderBy(Beatmap.uploaded to SortOrder.DESC)
                .limit(pageSize)

            Beatmap
                .joinVersions(true)
                .joinUploader()
                .joinCurator()
                .joinBookmarked(sess?.userId)
                .joinCollaborators()
                .select {
                    Beatmap.id.inSubQuery(
                        collabQuery.unionAll(uploadQuery).alias("tm").let { u ->
                            u
                                .slice(u[Collaboration.mapId])
                                .selectAll()
                                .orderBy(u[Collaboration.uploadedAt] to SortOrder.DESC)
                                .limit(20)
                        }
                    )
                }
                .complexToBeatmap()
                .map { map ->
                    MapDetail.from(map, cdnPrefix())
                }
                .sortedByDescending { it.uploaded }
        }

        call.respond(SearchResponse(beatmaps))
    }

    get<MapsApi.ByUploadDate>(
        "Get maps ordered by upload/publish/updated. If you're going to scrape the data and make 100s of requests make this this endpoint you use.".responds(
            ok<SearchResponse>()
        )
    ) {
        val sess = call.sessions.get<Session>()
        call.response.header("Access-Control-Allow-Origin", "*")

        val sortField = when (it.sort) {
            null, LatestSort.FIRST_PUBLISHED -> Beatmap.uploaded
            LatestSort.UPDATED -> Beatmap.updatedAt
            LatestSort.LAST_PUBLISHED -> Beatmap.lastPublishedAt
            LatestSort.CREATED -> Beatmap.createdAt
            LatestSort.CURATED -> Beatmap.curatedAt
        }

        val pageSize = (it.pageSize ?: 20).coerceIn(1, 100)
        val beatmaps = transaction {
            Beatmap
                .joinVersions(true)
                .joinUploader()
                .joinCurator()
                .joinBookmarked(sess?.userId)
                .select {
                    Beatmap.id.inSubQuery(
                        Beatmap
                            .joinVersions()
                            .slice(Beatmap.id)
                            .select {
                                Beatmap.deletedAt.isNull()
                                    .let { q ->
                                        if (it.automapper != true) q.and(Beatmap.automapper eq false) else q
                                    }
                                    .notNull(it.before) { o -> sortField less o.toJavaInstant() }
                                    .notNull(it.after) { o -> sortField greater o.toJavaInstant() }
                                    .let { q ->
                                        if (it.sort == LatestSort.CURATED) q.and(Beatmap.curatedAt.isNotNull()) else q
                                    }
                            }
                            .orderBy(sortField to (if (it.after != null) SortOrder.ASC else SortOrder.DESC))
                            .limit(pageSize)
                    )
                }
                .complexToBeatmap()
                .sortedByDescending { map ->
                    when (it.sort) {
                        null, LatestSort.FIRST_PUBLISHED -> map.uploaded
                        LatestSort.UPDATED -> map.updatedAt
                        LatestSort.LAST_PUBLISHED -> map.lastPublishedAt
                        LatestSort.CREATED -> map.createdAt
                        LatestSort.CURATED -> map.curatedAt
                    }
                }
                .map { map ->
                    MapDetail.from(map, cdnPrefix())
                }
        }

        call.respond(SearchResponse(beatmaps))
    }

    get<MapsApi.ByPlayCount>("Get maps ordered by play count (Not currently tracked)".responds(ok<SearchResponse>())) {
        call.response.header("Access-Control-Allow-Origin", "*")
        val beatmaps = transaction {
            Beatmap
                .joinVersions(true)
                .joinUploader()
                .joinCurator()
                .select {
                    Beatmap.id.inSubQuery(
                        Beatmap
                            .slice(Beatmap.id)
                            .select {
                                Beatmap.deletedAt.isNull()
                            }
                            .orderBy(Beatmap.plays to SortOrder.DESC)
                            .limit(it.page)
                    )
                }
                .complexToBeatmap()
                .map {
                    MapDetail.from(it, cdnPrefix())
                }
                .sortedByDescending { it.stats.plays }
        }

        call.respond(SearchResponse(beatmaps))
    }
}
