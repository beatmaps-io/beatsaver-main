@file:UseSerializers(LenientInstantSerializer::class, OptionalPropertySerializer::class)

package io.beatmaps.api

import de.nielsfalk.ktor.swagger.DefaultValue
import de.nielsfalk.ktor.swagger.Description
import de.nielsfalk.ktor.swagger.Ignore
import de.nielsfalk.ktor.swagger.ModelClass
import de.nielsfalk.ktor.swagger.notFound
import de.nielsfalk.ktor.swagger.ok
import de.nielsfalk.ktor.swagger.responds
import de.nielsfalk.ktor.swagger.version.shared.Group
import io.beatmaps.api.util.getWithOptions
import io.beatmaps.common.DeletedData
import io.beatmaps.common.FlagsEditData
import io.beatmaps.common.InfoEditData
import io.beatmaps.common.MapTag
import io.beatmaps.common.OptionalProperty
import io.beatmaps.common.OptionalPropertySerializer
import io.beatmaps.common.UnCurateMapData
import io.beatmaps.common.amqp.pub
import io.beatmaps.common.api.AiDeclarationType
import io.beatmaps.common.api.EAlertType
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.api.EPlaylistType
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.db.updateReturning
import io.beatmaps.common.dbo.Alert
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.BeatmapDao
import io.beatmaps.common.dbo.Collaboration
import io.beatmaps.common.dbo.Follows
import io.beatmaps.common.dbo.ModLog
import io.beatmaps.common.dbo.Playlist
import io.beatmaps.common.dbo.PlaylistDao
import io.beatmaps.common.dbo.PlaylistMap
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.common.dbo.joinBookmarked
import io.beatmaps.common.dbo.joinCollaborators
import io.beatmaps.common.dbo.joinCurator
import io.beatmaps.common.dbo.joinUploader
import io.beatmaps.common.dbo.joinVersions
import io.beatmaps.common.or
import io.beatmaps.common.solr.all
import io.beatmaps.common.solr.collections.BsSolr
import io.beatmaps.common.solr.field.eq
import io.beatmaps.common.solr.getIds
import io.beatmaps.common.solr.paged
import io.beatmaps.common.util.LenientInstantSerializer
import io.beatmaps.common.util.paramInfo
import io.beatmaps.common.util.requireParams
import io.beatmaps.login.Session
import io.beatmaps.util.cdnPrefix
import io.beatmaps.util.requireAuthorization
import io.beatmaps.util.updateAlertCount
import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.UseSerializers
import org.apache.solr.client.solrj.SolrQuery
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.lang.Integer.toHexString

@Resource("/api")
class MapsApi {
    @Resource("/maps/update")
    data class Update(
        @Ignore
        val api: MapsApi
    )

    @Resource("/maps/tagupdate")
    data class TagUpdate(
        @Ignore
        val api: MapsApi
    )

    @Resource("/maps/curate")
    data class Curate(
        @Ignore
        val api: MapsApi
    )

    @Resource("/maps/declareai")
    data class DeclareAi(
        @Ignore
        val api: MapsApi
    )

    @Resource("/maps/marknsfw")
    data class MarkNsfw(
        @Ignore
        val api: MapsApi
    )

    @Resource("/maps/wip/{page}")
    data class WIP(
        @ModelClass(Long::class)
        val page: OptionalProperty<Long>? = OptionalProperty.NotPresent,
        @Ignore
        val api: MapsApi
    ) {
        init {
            requireParams(
                paramInfo(WIP::page)
            )
        }
    }

    @Resource("/download/key/{key}")
    data class BeatsaverDownload(
        val key: String,
        @Ignore
        val api: MapsApi
    )

    @Resource("/maps/beatsaver/{key}")
    data class Beatsaver(
        val key: String,
        @Ignore
        val api: MapsApi
    )

    @Group("Maps")
    @Resource("/maps/id/{id}")
    data class Detail(
        val id: String,
        @Ignore
        val api: MapsApi
    )

    @Group("Maps")
    @Resource("/maps/ids/{ids}")
    data class ByIds(
        @Description("Up to 50 ids seperated by commas")
        val ids: String,
        @Ignore
        val api: MapsApi
    )

    @Resource("/maps/id/{id}/playlists")
    data class InPlaylists(
        val id: String,
        @Ignore
        val api: MapsApi
    )

    @Group("Maps")
    @Resource("/maps/hash/{hash}")
    data class ByHash(
        @Description("Up to 50 hashes seperated by commas")
        val hash: String,
        @Ignore
        val api: MapsApi
    )

    @Group("Maps")
    @Resource("/maps/uploader/{id}/{page}")
    data class ByUploader(
        @ModelClass(Int::class)
        val id: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        @ModelClass(Long::class) @DefaultValue("0")
        val page: OptionalProperty<Long>? = OptionalProperty.NotPresent,
        @Ignore
        val api: MapsApi
    ) {
        init {
            requireParams(
                paramInfo(ByUploader::id), paramInfo(ByUploader::page)
            )
        }
    }

    @Group("Maps")
    @Resource("/maps/collaborations/{id}")
    data class Collaborations(
        @ModelClass(Int::class)
        val id: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        @ModelClass(Instant::class)
        val before: OptionalProperty<Instant>? = OptionalProperty.NotPresent,
        @ModelClass(Int::class) @DefaultValue("20") @Description("1 - 100")
        val pageSize: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        @Ignore
        val api: MapsApi
    ) {
        init {
            requireParams(
                paramInfo(Collaborations::id), paramInfo(Collaborations::before), paramInfo(Collaborations::pageSize)
            )
        }
    }

    @Group("Maps")
    @Resource("/maps/latest")
    data class ByUploadDate(
        @ModelClass(Instant::class) @Description("You probably want this. Supplying the uploaded time of the last map in the previous page will get you another page.\nYYYY-MM-DDTHH:MM:SS+00:00")
        val before: OptionalProperty<Instant>? = OptionalProperty.NotPresent,
        @ModelClass(Instant::class) @Description("Like `before` but will get you maps more recent than the time supplied.\nYYYY-MM-DDTHH:MM:SS+00:00")
        val after: OptionalProperty<Instant>? = OptionalProperty.NotPresent,
        @Description("true = both, false = no ai")
        val automapper: Boolean? = false,
        @ModelClass(LatestSort::class)
        val sort: OptionalProperty<LatestSort>? = OptionalProperty.NotPresent,
        @ModelClass(Int::class) @Description("1 - 100") @DefaultValue("20")
        val pageSize: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        @Ignore
        val api: MapsApi
    ) {
        init {
            requireParams(
                paramInfo(ByUploadDate::before), paramInfo(ByUploadDate::after), paramInfo(ByUploadDate::sort), paramInfo(ByUploadDate::pageSize)
            )
        }
    }

    @Group("Maps")
    @Resource("/maps/deleted")
    data class Deleted(
        @ModelClass(Instant::class) @Description("You probably want this. Supplying the deleted time of the last map in the previous page will get you another page.\nYYYY-MM-DDTHH:MM:SS+00:00")
        val before: OptionalProperty<Instant>? = OptionalProperty.NotPresent,
        @ModelClass(Instant::class) @Description("Like `before` but will get you maps deleted more recently than the time supplied.\nYYYY-MM-DDTHH:MM:SS+00:00")
        val after: OptionalProperty<Instant>? = OptionalProperty.NotPresent,
        @ModelClass(Int::class) @Description("1 - 100") @DefaultValue("20")
        val pageSize: OptionalProperty<Int> = OptionalProperty.NotPresent,
        @Ignore
        val api: MapsApi
    ) {
        init {
            requireParams(
                paramInfo(Deleted::before), paramInfo(Deleted::after), paramInfo(Deleted::pageSize)
            )
        }
    }

    @Group("Maps")
    @Resource("/maps/plays/{page}")
    data class ByPlayCount(
        @ModelClass(Int::class) @DefaultValue("0")
        val page: OptionalProperty<Long>? = OptionalProperty.NotPresent,
        @Ignore
        val api: MapsApi
    ) {
        init {
            requireParams(
                paramInfo(ByPlayCount::page)
            )
        }
    }

    @Group("Users")
    @Resource("/users/id/{id}")
    data class UserId(
        @ModelClass(Int::class)
        val id: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        @Ignore
        val api: MapsApi
    ) {
        init {
            requireParams(
                paramInfo(UserId::id)
            )
        }
    }

    @Group("Users")
    @Resource("/users/ids/{ids}")
    data class UserIds(
        val ids: String,
        @Ignore
        val api: MapsApi
    )

    @Group("Users")
    @Resource("/users/name/{name}")
    data class UserName(
        val name: String,
        @Ignore
        val api: MapsApi
    )

    @Group("Users")
    @Resource("/users/verify")
    data class Verify(
        @Ignore
        val api: MapsApi
    )
}

enum class LatestSort {
    FIRST_PUBLISHED, UPDATED, LAST_PUBLISHED, CREATED, CURATED
}

fun Route.mapDetailRoute() {
    post<MapsApi.Curate> {
        requireAuthorization { _, user ->
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
                                it[curatedAt] = NowExpression(curatedAt)
                                it[curator] = user.userId
                            } else {
                                it[curatedAt] = null
                                it[curator] = null
                            }
                            it[updatedAt] = NowExpression(updatedAt)
                        }

                    (curateMap() > 0).also { success ->
                        if (success) {
                            Beatmap.joinUploader().selectAll().where {
                                Beatmap.id eq mapUpdate.id
                            }.complexToBeatmap().single().let {
                                // Handle alerts for curation
                                // When curating we send alerts to the uploader and their followers
                                // When uncurating we just send alerts to the uploader
                                if (mapUpdate.curated) {
                                    if (it.uploader.curationAlerts) {
                                        Alert.insert(
                                            "Your map has been curated",
                                            "@${user.uniqueName} just curated your map #${toHexString(mapUpdate.id)}: **${it.name}**.\n" +
                                                "Congratulations!",
                                            EAlertType.Curation,
                                            it.uploader.id.value
                                        )
                                    }

                                    val recipients = Follows.selectAll().where {
                                        Follows.userId eq user.userId and Follows.curation and Follows.following
                                    }.map { row ->
                                        row[Follows.followerId].value
                                    }

                                    Alert.insert(
                                        "Followed Curation",
                                        "@${user.uniqueName} just curated #${toHexString(mapUpdate.id)}: **${it.name}**.\n" +
                                            "*\"${Alert.forDescription(it.description)}\"*",
                                        EAlertType.MapCurated,
                                        recipients
                                    )
                                    updateAlertCount(recipients.plus(it.uploader.id.value))
                                } else {
                                    Alert.insert(
                                        "Your map has been uncurated",
                                        "@${user.uniqueName} just uncurated your map #${toHexString(mapUpdate.id)}: **${it.name}**.\n" +
                                            "Reason: *\"${mapUpdate.reason ?: ""}\"*",
                                        EAlertType.Uncuration,
                                        it.uploader.id.value
                                    )
                                    updateAlertCount(it.uploader.id.value)

                                    ModLog.insert(user.userId, mapUpdate.id, UnCurateMapData(mapUpdate.reason), it.uploader.id.value)
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

    post<MapsApi.DeclareAi> {
        requireAuthorization { _, user ->
            val mapUpdate = call.receive<AiDeclaration>()
            val result = transaction {
                val admin = user.isAdmin()

                transaction {
                    Beatmap.updateReturning(
                        {
                            (Beatmap.id eq mapUpdate.id).let { q ->
                                if (admin) {
                                    q // If current user is admin don't check the user
                                } else {
                                    q and (Beatmap.uploader eq user.userId)
                                }
                            }
                        }, {
                            it[declaredAi] = when {
                                !mapUpdate.automapper && admin -> AiDeclarationType.None
                                mapUpdate.automapper && admin -> AiDeclarationType.Admin
                                else -> AiDeclarationType.Uploader
                            }
                            it[updatedAt] = NowExpression(updatedAt)
                        },
                        Beatmap.uploader
                    ).let { rows ->
                        (!rows.isNullOrEmpty()).also { success ->
                            if (success && admin && rows != null) {
                                ModLog.insert(
                                    user.userId,
                                    mapUpdate.id,
                                    FlagsEditData(ai = mapUpdate.automapper),
                                    rows.first()[Beatmap.uploader].value
                                )
                            }
                        }
                    }
                }
            }

            if (result) call.pub("beatmaps", "maps.${mapUpdate.id}.updated.ai", null, mapUpdate.id)
            call.respond(if (result) HttpStatusCode.OK else HttpStatusCode.BadRequest)
        }
    }

    post<MapsApi.MarkNsfw> {
        requireAuthorization { _, user ->
            if (!user.isAdmin()) {
                call.respond(HttpStatusCode.BadRequest)
            } else {
                val mapUpdate = call.receive<MarkNsfw>()
                val result = transaction {
                    Beatmap.updateReturning(
                        {
                            (Beatmap.id eq mapUpdate.id)
                        }, {
                            it[nsfw] = mapUpdate.nsfw
                            it[updatedAt] = NowExpression(updatedAt)
                        },
                        Beatmap.uploader
                    ).let { rows ->
                        (!rows.isNullOrEmpty()).also { success ->
                            if (success && rows != null) {
                                ModLog.insert(
                                    user.userId,
                                    mapUpdate.id,
                                    FlagsEditData(nsfw = mapUpdate.nsfw),
                                    rows.first()[Beatmap.uploader].value
                                )
                            }
                        }
                    }
                }

                if (result) call.pub("beatmaps", "maps.${mapUpdate.id}.updated.nsfw", null, mapUpdate.id)
                call.respond(if (result) HttpStatusCode.OK else HttpStatusCode.BadRequest)
            }
        }
    }

    post<MapsApi.Update> {
        requireAuthorization { _, user ->
            val mapUpdate = call.receive<MapInfoUpdate>()

            val tooMany = mapUpdate.tags?.groupBy { it.type }?.mapValues { it.value.size }?.withDefault { 0 }?.let { byType ->
                MapTag.maxPerType.any { byType.getValue(it.key) > it.value }
            }

            val result = transaction {
                val oldData = if (user.isAdmin()) {
                    BeatmapDao.wrapRow(Beatmap.selectAll().where { Beatmap.id eq mapUpdate.id }.single())
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
                            it[deletedAt] = NowExpression(deletedAt)
                        } else {
                            mapUpdate.name?.let { n -> it[name] = n.take(MapConstants.MAX_NAME_LENGTH) }
                            mapUpdate.description?.let { d -> it[description] = d.take(MapConstants.MAX_DESCRIPTION_LENGTH) }
                            if (tooMany != true) { // Don't update tags if request is trying to add too many tags
                                mapUpdate.tags?.filter { t -> t != MapTag.None }?.map { t -> t.slug }?.let { t -> it[tags] = t }
                            }
                            it[updatedAt] = NowExpression(updatedAt)
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
                                InfoEditData(
                                    oldData.name,
                                    oldData.description,
                                    mapUpdate.name ?: "",
                                    mapUpdate.description ?: "",
                                    oldData.tags?.mapNotNull { MapTag.fromSlug(it) },
                                    mapUpdate.tags
                                )
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

                            updateAlertCount(oldData.uploaderId.value)
                        }
                    }

                    if (mapUpdate.deleted) {
                        val alertIds = Collaboration.deleteForMap(mapUpdate.id)
                        updateAlertCount(alertIds)
                    }
                }
            }

            val updateType = if (mapUpdate.deleted) "delete" else "info"
            if (result) call.pub("beatmaps", "maps.${mapUpdate.id}.updated.$updateType", null, mapUpdate.id)
            call.respond(if (result) HttpStatusCode.OK else HttpStatusCode.BadRequest)
        }
    }

    post<MapsApi.TagUpdate> {
        requireAuthorization { _, user ->
            val mapUpdate = call.receive<SimpleMapInfoUpdate>()

            val tooMany = mapUpdate.tags?.groupBy { it.type }?.mapValues { it.value.size }?.withDefault { 0 }?.let { byType ->
                MapTag.maxPerType.any { byType.getValue(it.key) > it.value }
            }

            val result = if (tooMany != true && user.isCurator()) {
                transaction {
                    val oldData = BeatmapDao.wrapRow(Beatmap.selectAll().where { Beatmap.id eq mapUpdate.id }.single())

                    fun updateMap() =
                        Beatmap.update({
                            Beatmap.id eq mapUpdate.id and Beatmap.deletedAt.isNull()
                        }) {
                            mapUpdate.tags?.filter { t -> t != MapTag.None }?.map { t -> t.slug }?.let { t -> it[tags] = t }
                            it[updatedAt] = NowExpression(updatedAt)
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

    getWithOptions<MapsApi.Detail>("Get map information".responds(ok<MapDetail>(), notFound())) {
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
                    .selectAll()
                    .where {
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

        requireAuthorization { _, sess ->
            try {
                transaction {
                    Playlist.joinMaps {
                        PlaylistMap.mapId eq mapId.toInt(16)
                    }.selectAll().where {
                        Playlist.owner eq sess.userId and Playlist.deletedAt.isNull() and (Playlist.type neq EPlaylistType.Search)
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

    getWithOptions<MapsApi.Beatsaver> {
        val r = transaction {
            Beatmap
                .joinVersions(true)
                .joinUploader()
                .joinCurator()
                .selectAll()
                .where {
                    Beatmap.id.inSubQuery(
                        Versions
                            .select(Versions.mapId)
                            .where {
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
                    .selectAll()
                    .where {
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

    getWithOptions<MapsApi.ByIds>("Get maps for mapIds".responds(ok<MapDetail>(), notFound())) { ids ->
        val sess = call.sessions.get<Session>()
        val mapIdList = ids.ids.split(",").take(50)
        val isAdmin = sess?.isAdmin() == true
        val r = try {
            transaction {
                Beatmap
                    .joinVersions(true, state = null)
                    .joinUploader()
                    .joinCurator()
                    .joinBookmarked(sess?.userId)
                    .joinCollaborators()
                    .selectAll()
                    .where {
                        Beatmap.id.inList(
                            mapIdList.mapNotNull { id -> id.toIntOrNull(16) }
                        ) and (Beatmap.deletedAt.isNull())
                    }
                    .complexToBeatmap()
                    .map {
                        MapDetail.from(it, cdnPrefix())
                    }.filter {
                        it.publishedVersion() != null || it.uploader.id == sess?.userId || sess?.testplay == true || isAdmin
                    }
                    .associateBy { it.id }
            }
        } catch (_: NumberFormatException) {
            null
        }
        if (r == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respond(r)
        }
    }

    getWithOptions<MapsApi.ByHash>("Get map(s) for a map hash".responds(ok<MapDetail>(), notFound())) {
        val r = transaction {
            val rawHashes = it.hash.lowercase().split(',', ignoreCase = false).take(50)
            val singleRequest = rawHashes.size <= 1

            val versions = Versions
                .select(Versions.mapId, Versions.hash)
                .where {
                    Versions.hash.inList(rawHashes)
                }
            val versionMapping = versions.associate { it[Versions.hash] to it[Versions.mapId].value }
            val mapIds = versionMapping.values.toHashSet()

            Beatmap
                .joinVersions(true)
                .joinUploader()
                .joinCurator()
                .joinCollaborators()
                .selectAll()
                .where {
                    Beatmap.id.inList(mapIds) and (Beatmap.deletedAt.isNull())
                }
                .complexToBeatmap()
                .map {
                    MapDetail.from(it, cdnPrefix())
                }.let { maps ->
                    val assocMaps = maps.associateBy { it.id }
                    when (singleRequest) {
                        true -> maps.firstOrNull()
                        else -> {
                            rawHashes.associateWith {
                                versionMapping[it]?.let { mapId ->
                                    assocMaps[toHexString(mapId)]
                                }
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
        requireAuthorization { _, sess ->
            val beatmaps = transaction {
                Beatmap
                    .joinVersions(true, state = null)
                    .joinUploader()
                    .joinCurator()
                    .joinBookmarked(sess.userId)
                    .selectAll()
                    .where {
                        Beatmap.id.inSubQuery(
                            Beatmap
                                .join(
                                    Versions,
                                    JoinType.LEFT,
                                    onColumn = Beatmap.id,
                                    otherColumn = Versions.mapId,
                                    additionalConstraint = { Versions.state eq EMapState.Published }
                                )
                                .select(Beatmap.id)
                                .where {
                                    Beatmap.uploader.eq(sess.userId) and Beatmap.deletedAt.isNull() and Versions.mapId.isNull()
                                }
                                .groupBy(Beatmap.id)
                                .orderBy(Beatmap.uploaded to SortOrder.DESC)
                                .limit(r.page.or(0))
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

    getWithOptions<MapsApi.ByUploader>("Get maps by a user".responds(ok<SearchResponse>())) {
        val sess = call.sessions.get<Session>()
        val beatmaps = transaction {
            Beatmap
                .joinVersions(true)
                .joinUploader()
                .joinCurator()
                .joinBookmarked(sess?.userId)
                .selectAll()
                .where {
                    Beatmap.id.inSubQuery(
                        Beatmap
                            .joinVersions()
                            .select(Beatmap.id)
                            .where {
                                Beatmap.uploader.eq(it.id?.orNull()) and (Beatmap.deletedAt.isNull())
                            }
                            .orderBy(Beatmap.uploaded to SortOrder.DESC)
                            .limit(it.page.or(0))
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

    getWithOptions<MapsApi.Collaborations>("Get maps by a user, including collaborations".responds(ok<SearchResponse>())) {
        val sess = call.sessions.get<Session>()

        val results = SolrQuery().all()
            .notNullOpt(it.before) { o -> BsSolr.uploaded less o }
            .notNullOpt(it.id) { o -> BsSolr.mapperIds eq o }
            .setSort(BsSolr.uploaded.desc())
            .paged(pageSize = it.pageSize.or(20).coerceIn(1, 100))
            .getIds(BsSolr, call = call)

        val beatmaps = newSuspendedTransaction {
            Beatmap
                .joinVersions(true)
                .joinUploader()
                .joinCurator()
                .joinBookmarked(sess?.userId)
                .joinCollaborators()
                .selectAll()
                .where {
                    Beatmap.id.inList(results.mapIds)
                }
                .complexToBeatmap()
                .map { map ->
                    MapDetail.from(map, cdnPrefix())
                }
                .sortedByDescending { it.uploaded }
        }

        call.respond(SearchResponse(beatmaps, results.searchInfo))
    }

    getWithOptions<MapsApi.ByUploadDate>(
        "Get maps ordered by upload/publish/updated. If you're going to scrape the data and make 100s of requests make this this endpoint you use.".responds(
            ok<SearchResponse>()
        )
    ) {
        val sess = call.sessions.get<Session>()

        val sortField = when (it.sort?.orNull()) {
            null, LatestSort.FIRST_PUBLISHED -> Beatmap.uploaded
            LatestSort.UPDATED -> Beatmap.updatedAt
            LatestSort.LAST_PUBLISHED -> Beatmap.lastPublishedAt
            LatestSort.CREATED -> Beatmap.createdAt
            LatestSort.CURATED -> Beatmap.curatedAt
        }

        val beatmaps = transaction {
            Beatmap
                .joinVersions(true)
                .joinUploader()
                .joinCurator()
                .joinBookmarked(sess?.userId)
                .selectAll()
                .where {
                    Beatmap.id.inSubQuery(
                        Beatmap
                            .joinVersions()
                            .joinUploader()
                            .select(Beatmap.id)
                            .where {
                                Beatmap.deletedAt.isNull()
                                    .let { q ->
                                        if (it.automapper != true) q.and(Beatmap.declaredAi eq AiDeclarationType.None) else q
                                    }
                                    .notNullOpt(it.before) { o -> sortField less o.toJavaInstant() }
                                    .notNullOpt(it.after) { o -> sortField greater o.toJavaInstant() }
                                    .let { q ->
                                        if (it.sort?.orNull() == LatestSort.CURATED) q.and(Beatmap.curatedAt.isNotNull()) else q
                                    }
                            }
                            .orderBy(sortField to (if (it.after?.orNull() != null) SortOrder.ASC else SortOrder.DESC))
                            .limit(it.pageSize.or(20).coerceIn(1, 100))
                    )
                }
                .complexToBeatmap()
                .sortedByDescending { map ->
                    when (it.sort?.orNull()) {
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

    getWithOptions<MapsApi.Deleted>(
        "Get deleted maps since or before a certain date.".responds(
            ok<DeletedResponse>()
        )
    ) {
        val sortField = Beatmap.deletedAt
        val beatmaps = transaction {
            Beatmap
                .select(Beatmap.id, Beatmap.deletedAt)
                .where {
                    Beatmap.deletedAt.isNotNull()
                        .notNullOpt(it.before) { o -> sortField less o.toJavaInstant() }
                        .notNullOpt(it.after) { o -> sortField greater o.toJavaInstant() }
                }
                .orderBy(sortField to (if (it.after?.orNull() != null) SortOrder.ASC else SortOrder.DESC))
                .limit(it.pageSize.or(20).coerceIn(1, 100))
                .mapNotNull { map ->
                    val instant = map[Beatmap.deletedAt]
                    if (instant == null) {
                        null
                    } else {
                        DeletedMap(toHexString(map[Beatmap.id].value), instant.toKotlinInstant())
                    }
                }
                .sortedByDescending { map -> map.deletedAt }
        }

        call.respond(DeletedResponse(beatmaps))
    }

    getWithOptions<MapsApi.ByPlayCount>("Get maps ordered by play count (Not currently tracked)".responds(ok<SearchResponse>())) {
        val beatmaps = transaction {
            Beatmap
                .joinVersions(true)
                .joinUploader()
                .joinCurator()
                .selectAll()
                .where {
                    Beatmap.id.inSubQuery(
                        Beatmap
                            .select(Beatmap.id)
                            .where {
                                Beatmap.deletedAt.isNull()
                            }
                            .orderBy(Beatmap.plays to SortOrder.DESC)
                            .limit(it.page.or(0))
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
