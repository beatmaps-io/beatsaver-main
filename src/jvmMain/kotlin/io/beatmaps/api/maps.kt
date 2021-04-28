package io.beatmaps.api

import de.nielsfalk.ktor.swagger.*
import de.nielsfalk.ktor.swagger.version.shared.Group
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.consumeAck
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Beatmap.joinUploader
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.common.inlineJackson
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import pl.jutupe.ktor_rabbitmq.RabbitMQ
import pl.jutupe.ktor_rabbitmq.publish

@Location("/api") class MapsApi {
    @Location("/stream") class Stream {
        @Group("Maps") @Location("/maps/latest") data class New(val after: Instant? = null, val automapper: Boolean = false, @Ignore val api: Stream)
    }
    @Location("/maps/update") data class Update(val api: MapsApi)
    @Location("/maps/wip/{page?}") data class WIP(val page: Long? = 0, val api: MapsApi)
    @Group("Maps") @Location("/maps/beatsaver/{key}") data class Beatsaver(val key: String, @Ignore val api: MapsApi)
    @Group("Maps") @Location("/maps/id/{key}") data class Detail(val key: Int, @Ignore val api: MapsApi)
    @Group("Maps") @Location("/maps/hash/{hash}") data class ByHash(val hash: String, @Ignore val api: MapsApi)
    @Group("Maps") @Location("/maps/uploader/{id}/{page?}") data class ByUploader(val id: Int, val page: Long? = 0, @Ignore  val api: MapsApi)
    @Group("Maps") @Location("/maps/latest") data class ByUploadDate(val after: Instant? = null, val automapper: Boolean = false, @Ignore  val api: MapsApi)
    @Group("Maps") @Location("/maps/plays/{page?}") data class ByPlayCount(val page: Long? = 0, @Ignore val api: MapsApi)
    @Group("Users") @Location("/users/id/{id}") data class UserId(val id: Int, @Ignore val api: MapsApi)
    @Group("Users") @Location("/users/verify") data class Verify(@Ignore val api: MapsApi)
}

fun Route.mapDetailRoute(mq: RabbitMQ) {
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

    val activeChannels = mutableListOf<Channel<String>>()
    mq.consumeAck("bm.updateStream", Int::class) { _, mapId ->
        transaction {
            Beatmap
                .joinVersions(true, null) // Allow returning non-published versions
                .joinUploader()
                .select {
                    Beatmap.id eq mapId and (Beatmap.deletedAt.isNull())
                }
                .complexToBeatmap()
                .firstOrNull()
                ?.enrichTestplays()
                ?.run {
                    MapDetail.from(this)
                }
        }?.let { map ->
            activeChannels.forEach { it.send(inlineJackson.writeValueAsString(map)) }
        }
    }

    get<MapsApi.Stream.New> {
        val channel = Channel<String>(10)
        channel.send("")
        activeChannels.add(channel)

        try {
            call.respondTextWriter {
                while (true) {
                    // Send every 30 seconds to keep the connection alive
                    val map = withTimeoutOrNull(30 * 1000) {
                        channel.receive()
                    } ?: ""
                    withContext(Dispatchers.IO) {
                        write(map + "\n")
                        flush()
                    }
                }
            }
        } catch (e: CancellationException) {
            // Client closed connection
        } finally {
            activeChannels.remove(channel)
            channel.close()
        }
    }

    post<MapsApi.Update> {
        call.response.header("Access-Control-Allow-Origin", "*")
        requireAuthorization { user ->
            val mapUpdate = call.receive<MapInfoUpdate>()

            val result = transaction {
                Beatmap.update({
                    Beatmap.uploader eq user.userId and (Beatmap.id eq mapUpdate.id)
                }) {
                    if (mapUpdate.deleted) {
                        it[deletedAt] = NowExpression(deletedAt.columnType)
                    } else {
                        mapUpdate.name?.let { n -> it[name] = n }
                        mapUpdate.description?.let { d -> it[description] = d }
                    }
                } > 0 
            }

            if (result) call.publish("beatmaps", "maps.${mapUpdate.id}.updated", null, mapUpdate.id)
            call.respond(if (result) HttpStatusCode.OK else HttpStatusCode.BadRequest)
        }
    }

    get<MapsApi.Detail>("Get map information".responds(ok<MapDetail>()).responds(notFound())) {
        call.response.header("Access-Control-Allow-Origin", "*")
        val r = transaction {
            Beatmap
                .joinVersions(true, null) // Allow returning non-published versions
                .joinUploader()
                .select {
                    Beatmap.id eq it.key and (Beatmap.deletedAt.isNull())
                }
                .complexToBeatmap()
                .firstOrNull()
                ?.enrichTestplays()
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

    get<MapsApi.Beatsaver>("Get map for a beatsaver key".responds(ok<MapDetail>()).responds(notFound())) {
        call.response.header("Access-Control-Allow-Origin", "*")
        val r = transaction {
            Beatmap
                .joinVersions(true)
                .joinUploader()
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

    get<MapsApi.ByHash>("Get map for a map hash".responds(ok<MapDetail>()).responds(notFound())) {
        call.response.header("Access-Control-Allow-Origin", "*")
        val r = transaction {
            Beatmap
                .joinVersions(true)
                .joinUploader()
                .select {
                    Versions.hash.eq(it.hash) and (Beatmap.deletedAt.isNull())
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
                .select {
                    Beatmap.id.inSubQuery(
                        Beatmap
                            .slice(Beatmap.id)
                            .select {
                                Beatmap.deletedAt.isNull().let { q ->
                                    if (!it.automapper) q.and(Beatmap.automapper eq false) else q
                                }.let { q ->
                                    if (it.after != null) q.and(Beatmap.uploaded less it.after.toJavaInstant()) else q
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