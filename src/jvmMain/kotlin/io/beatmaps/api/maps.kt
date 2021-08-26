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
import io.beatmaps.common.DeletedData
import io.beatmaps.common.InfoEditData
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Beatmap.joinCurator
import io.beatmaps.common.dbo.Beatmap.joinUploader
import io.beatmaps.common.dbo.BeatmapDao
import io.beatmaps.common.dbo.ModLog
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.common.pub
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.locations.options
import io.ktor.locations.post
import io.ktor.request.receive
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.routing.Route
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.lang.Integer.toHexString

@Location("/api") class MapsApi {
    @Location("/maps/update") data class Update(val api: MapsApi)
    @Location("/maps/curate") data class Curate(val api: MapsApi)
    @Location("/maps/wip/{page}") data class WIP(val page: Long = 0, val api: MapsApi)
    @Location("/download/key/{key}") data class BeatsaverDownload(val key: String, val api: MapsApi)
    @Location("/maps/beatsaver/{key}") data class Beatsaver(val key: String, @Ignore val api: MapsApi)
    @Group("Maps") @Location("/maps/id/{id}") data class Detail(val id: String, @Ignore val api: MapsApi)
    @Group("Maps") @Location("/maps/hash/{hash}") data class ByHash(
        @Description("Up to 50 hashes seperated by commas")
        val hash: String,
        @Ignore
        val api: MapsApi
    )
    @Group("Maps") @Location("/maps/uploader/{id}/{page}") data class ByUploader(val id: Int, @DefaultValue("0") val page: Long = 0, @Ignore val api: MapsApi)
    @Group("Maps") @Location("/maps/latest") data class ByUploadDate(
        @Description("You probably want this. Supplying the uploaded time of the last map in the previous page will get you another page.\nYYYY-MM-DDTHH:MM:SS+00:00")
        val before: Instant? = null,
        @Description("Like `before` but will get you maps more recent than the time supplied.\nYYYY-MM-DDTHH:MM:SS+00:00")
        val after: Instant? = null,
        @Description("true = both, false = no ai")
        val automapper: Boolean? = false,
        val sort: LatestSort? = LatestSort.FIRST_PUBLISHED,
        @Ignore
        val api: MapsApi
    )
    @Group("Maps") @Location("/maps/plays/{page}") data class ByPlayCount(@DefaultValue("0") val page: Long = 0, @Ignore val api: MapsApi)
    @Group("Users") @Location("/users/id/{id}") data class UserId(val id: Int, @Ignore val api: MapsApi)
    @Group("Users") @Location("/users/name/{name}") data class UserName(val name: String, @Ignore val api: MapsApi)
    @Group("Users") @Location("/users/verify") data class Verify(@Ignore val api: MapsApi)
}

enum class LatestSort {
    FIRST_PUBLISHED, UPDATED, LAST_PUBLISHED, CREATED
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
            if (!user.admin) {
                call.respond(HttpStatusCode.BadRequest)
            } else {
                val mapUpdate = call.receive<CurateMap>()

                val result = transaction {
                    Beatmap.update({
                        (Beatmap.id eq mapUpdate.id)
                    }) {
                        if (mapUpdate.curated) {
                            it[curatedAt] = NowExpression(deletedAt.columnType)
                            it[curator] = EntityID(user.userId, User)
                        } else {
                            it[curatedAt] = null
                            it[curator] = null
                        }
                        it[updatedAt] = NowExpression(updatedAt.columnType)
                    } > 0
                }

                if (result) call.pub("beatmaps", "maps.${mapUpdate.id}.updated", null, mapUpdate.id)
                call.respond(if (result) HttpStatusCode.OK else HttpStatusCode.BadRequest)
            }
        }
    }

    post<MapsApi.Update> {
        call.response.header("Access-Control-Allow-Origin", "*")
        requireAuthorization { user ->
            val mapUpdate = call.receive<MapInfoUpdate>()

            val result = transaction {
                val oldData = if (user.admin) {
                    BeatmapDao.wrapRow(Beatmap.select { Beatmap.id eq mapUpdate.id }.single())
                } else {
                    null
                }

                fun updateMap() =
                    Beatmap.update({
                        (Beatmap.id eq mapUpdate.id).let { q ->
                            if (user.admin) {
                                q // If current user is admin don't check the user
                            } else {
                                q and (Beatmap.uploader eq user.userId)
                            }
                        }
                    }) {
                        if (mapUpdate.deleted) {
                            it[deletedAt] = NowExpression(deletedAt.columnType)
                        } else {
                            mapUpdate.name?.let { n -> it[name] = n }
                            mapUpdate.description?.let { d -> it[description] = d }
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
                                InfoEditData(oldData.name, oldData.description, mapUpdate.name ?: "", mapUpdate.description ?: "")
                            }
                        )
                    }
                }
            }

            if (result) call.pub("beatmaps", "maps.${mapUpdate.id}.updated", null, mapUpdate.id)
            call.respond(if (result) HttpStatusCode.OK else HttpStatusCode.BadRequest)
        }
    }

    get<MapsApi.Detail>("Get map information".responds(ok<MapDetail>()).responds(notFound())) {
        call.response.header("Access-Control-Allow-Origin", "*")
        val r = try {
            transaction {
                Beatmap
                    .joinVersions(true, null) // Allow returning non-published versions
                    .joinUploader()
                    .joinCurator()
                    .select {
                        Beatmap.id eq it.id.toInt(16) and (Beatmap.deletedAt.isNull())
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

        if (r == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respond(r)
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

    get<MapsApi.ByHash>("Get map(s) for a map hash".responds(ok<MapDetail>()).responds(notFound())) {
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
        requireAuthorization {
            val beatmaps = transaction {
                Beatmap
                    .joinVersions(true, null)
                    .joinUploader()
                    .joinCurator()
                    .select {
                        Beatmap.id.inSubQuery(
                            Beatmap
                                .join(Versions, JoinType.LEFT, onColumn = Beatmap.id, otherColumn = Versions.mapId, additionalConstraint = { Versions.state eq EMapState.Published })
                                .slice(Beatmap.id)
                                .select {
                                    Beatmap.uploader.eq(it.userId) and Beatmap.deletedAt.isNull() and Versions.mapId.isNull()
                                }
                                .groupBy(Beatmap.id)
                                .orderBy(Beatmap.uploaded to SortOrder.DESC)
                                .limit(r.page)
                        )
                    }
                    .complexToBeatmap()
                    .map {
                        MapDetail.from(it, cdnPrefix())
                    }
                    .sortedByDescending { it.uploaded }
            }

            call.respond(SearchResponse(beatmaps))
        }
    }

    get<MapsApi.ByUploader>("Get maps by a user".responds(ok<SearchResponse>())) {
        call.response.header("Access-Control-Allow-Origin", "*")
        val beatmaps = transaction {
            Beatmap
                .joinVersions(true)
                .joinUploader()
                .joinCurator()
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
                .map {
                    MapDetail.from(it, cdnPrefix())
                }
                .sortedByDescending { it.uploaded }
        }

        call.respond(SearchResponse(beatmaps))
    }

    get<MapsApi.ByUploadDate>("Get maps ordered by upload/publish/updated. If you're going to scrape the data and make 100s of requests make this this endpoint you use.".responds(ok<SearchResponse>())) {
        call.response.header("Access-Control-Allow-Origin", "*")

        val sortField = when (it.sort) {
            null, LatestSort.FIRST_PUBLISHED -> Beatmap.uploaded
            LatestSort.UPDATED -> Beatmap.updatedAt
            LatestSort.LAST_PUBLISHED -> Beatmap.lastPublishedAt
            LatestSort.CREATED -> Beatmap.createdAt
        }

        val beatmaps = transaction {
            Beatmap
                .joinVersions(true)
                .joinUploader()
                .joinCurator()
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
                            }
                            .orderBy(sortField to (if (it.after != null) SortOrder.ASC else SortOrder.DESC))
                            .limit(20)
                    )
                }
                .complexToBeatmap()
                .sortedByDescending { map ->
                    when (it.sort) {
                        null, LatestSort.FIRST_PUBLISHED -> map.uploaded
                        LatestSort.UPDATED -> map.updatedAt
                        LatestSort.LAST_PUBLISHED -> map.lastPublishedAt
                        LatestSort.CREATED -> map.createdAt
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
