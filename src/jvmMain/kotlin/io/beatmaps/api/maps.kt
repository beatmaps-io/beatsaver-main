package io.beatmaps.api

import de.nielsfalk.ktor.swagger.DefaultValue
import de.nielsfalk.ktor.swagger.Ignore
import de.nielsfalk.ktor.swagger.get
import de.nielsfalk.ktor.swagger.notFound
import de.nielsfalk.ktor.swagger.ok
import de.nielsfalk.ktor.swagger.responds
import de.nielsfalk.ktor.swagger.version.shared.Group
import io.beatmaps.common.Config
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
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import pl.jutupe.ktor_rabbitmq.publish

@Location("/api") class MapsApi {
    @Location("/maps/update") data class Update(val api: MapsApi)
    @Location("/maps/curate") data class Curate(val api: MapsApi)
    @Location("/maps/wip/{page}") data class WIP(val page: Long = 0, val api: MapsApi)
    @Location("/download/key/{key}") data class BeatsaverDownload(val key: String, val api: MapsApi)
    @Group("Maps") @Location("/maps/beatsaver/{key}") data class Beatsaver(val key: String, @Ignore val api: MapsApi)
    @Group("Maps") @Location("/maps/id/{id}") data class Detail(val id: String, @Ignore val api: MapsApi)
    @Group("Maps") @Location("/maps/hash/{hash}") data class ByHash(val hash: String, @Ignore val api: MapsApi)
    @Group("Maps") @Location("/maps/uploader/{id}/{page}") data class ByUploader(val id: Int, @DefaultValue("0") val page: Long = 0, @Ignore  val api: MapsApi)
    @Group("Maps") @Location("/maps/latest") data class ByUploadDate(@Ignore val after: Instant? = null, val before: Instant? = null, val automapper: Boolean = false, @Ignore  val api: MapsApi)
    @Group("Maps") @Location("/maps/plays/{page}") data class ByPlayCount(@DefaultValue("0") val page: Long = 0, @Ignore val api: MapsApi)
    @Group("Users") @Location("/users/id/{id}") data class UserId(val id: Int, @Ignore val api: MapsApi)
    @Group("Users") @Location("/users/verify") data class Verify(@Ignore val api: MapsApi)
}

fun Route.mapDetailRoute() {
    options<MapsApi.Beatsaver> {
        call.response.header("Access-Control-Allow-Origin", "*")
    }
    options<MapsApi.Detail> {
        call.response.header("Access-Control-Allow-Origin", "*")
    }
    options<MapsApi.ByHash> {
        call.response.header("Access-Control-Allow-Origin", "*")
    }
    options<MapsApi.ByUploader> {
        call.response.header("Access-Control-Allow-Origin", "*")
    }
    options<MapsApi.ByUploadDate> {
        call.response.header("Access-Control-Allow-Origin", "*")
    }
    options<MapsApi.ByPlayCount> {
        call.response.header("Access-Control-Allow-Origin", "*")
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

                (Beatmap.update({
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
                    }
                } > 0).also { rTemp ->
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
                        MapDetail.from(this)
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

    get<MapsApi.Beatsaver>("Get map for a beatsaver key".responds(ok<MapDetail>()).responds(notFound())) {
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
                    MapDetail.from(this)
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
                        MapDetail.from(this)
                    }
            }?.publishedVersion()
        } catch (_: NumberFormatException) {
            null
        }

        if (r == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respondRedirect("${Config.cdnbase}/${r.hash}.zip")
        }
    }

    get<MapsApi.ByHash>("Get map for a map hash".responds(ok<MapDetail>()).responds(notFound())) {
        call.response.header("Access-Control-Allow-Origin", "*")
        val r = transaction {
            Beatmap
                .joinVersions(true)
                .joinUploader()
                .joinCurator()
                .select {
                    Versions.hash.eq(it.hash.lowercase()) and (Beatmap.deletedAt.isNull())
                }
                .complexToBeatmap()
                .firstOrNull()
                ?.run {
                    MapDetail.from(this)
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
                    .joinVersions(true) { Versions.state neq EMapState.Published }
                    .joinUploader()
                    .joinCurator()
                    .select {
                        Beatmap.id.inSubQuery(
                            Beatmap
                                .slice(Beatmap.id)
                                .select {
                                    Beatmap.uploader.eq(it.userId) and (Beatmap.deletedAt.isNull())
                                }
                                .orderBy(Beatmap.uploaded to SortOrder.DESC)
                                .limit(r.page)
                        )
                    }
                    .complexToBeatmap()
                    .map {
                        MapDetail.from(it)
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
                    MapDetail.from(it)
                }
                .sortedByDescending { it.uploaded }
        }

        call.respond(SearchResponse(beatmaps))
    }

    get<MapsApi.ByUploadDate>("Get maps ordered by upload date".responds(ok<SearchResponse>())) {
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
                                Beatmap.deletedAt.isNull().let { q ->
                                    if (!it.automapper) q.and(Beatmap.automapper eq false) else q
                                }.let { q ->
                                    (it.before ?: it.after).let { p ->
                                        if (p != null) q.and(Beatmap.uploaded less p.toJavaInstant()) else q
                                    }
                                }
                            }
                            .orderBy(Beatmap.uploaded to SortOrder.DESC)
                            .limit(20)
                    )
                }
                .complexToBeatmap()
                .map {
                    MapDetail.from(it)
                }
                .sortedByDescending { it.uploaded }
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
                    MapDetail.from(it)
                }
                .sortedByDescending { it.stats.plays }
        }

        call.respond(SearchResponse(beatmaps))
    }
}