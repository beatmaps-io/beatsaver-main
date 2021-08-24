package io.beatmaps.websockets

import io.beatmaps.api.MapDetail
import io.beatmaps.api.from
import io.beatmaps.common.CDNUpdate
import io.beatmaps.common.consumeAck
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Beatmap.joinCurator
import io.beatmaps.common.dbo.Beatmap.joinUploader
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.common.inlineJackson
import io.beatmaps.common.rabbitOptional
import io.ktor.routing.Route
import io.ktor.routing.application
import io.ktor.websocket.webSocket
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import pl.jutupe.ktor_rabbitmq.publish
import java.lang.Integer.toHexString

fun Route.mapsWebsocket() {
    val holder = ChannelHolder()

    application.rabbitOptional {
        consumeAck("bm.updateStream", Int::class) { _, mapId ->
            transaction {
                Beatmap
                    .joinVersions(true, null)
                    .joinUploader()
                    .joinCurator()
                    .select {
                        Beatmap.id eq mapId
                    }
                    .complexToBeatmap()
                    .firstOrNull()?.let {
                        it.deletedAt to MapDetail.from(it, "")
                    }
            }?.let { map ->
                val publishedVersion = map.second.publishedVersion()
                val updatedVersion = publishedVersion ?: map.second.latestVersion()
                val cdnUpdate = CDNUpdate(updatedVersion?.hash, map.second.intId(), publishedVersion != null, map.second.metadata.songName, map.second.metadata.levelAuthorName, map.first != null)

                publish("beatmaps", "cdn.${cdnUpdate.mapId}", null, cdnUpdate)

                loopAndTerminateOnError(holder) {
                    if (map.first == null) {
                        it.send(inlineJackson.writeValueAsString(WebsocketMessage(WebsocketMessageType.MAP_UPDATE, map.second)))
                    } else {
                        it.send(inlineJackson.writeValueAsString(WebsocketMessage(WebsocketMessageType.MAP_DELETE, toHexString(mapId))))
                    }
                }
            }
        }
    }

    webSocket("/ws/maps") {
        websocketConnection(holder)
    }
}
