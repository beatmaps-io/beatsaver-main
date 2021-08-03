package io.beatmaps.websockets

import io.beatmaps.api.MapDetail
import io.beatmaps.api.from
import io.beatmaps.common.consumeAck
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Beatmap.joinCurator
import io.beatmaps.common.dbo.Beatmap.joinUploader
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.common.inlineJackson
import io.beatmaps.common.rabbitOptional
import io.ktor.http.cio.websocket.Frame
import io.ktor.routing.Route
import io.ktor.routing.application
import io.ktor.websocket.webSocket
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

enum class WebsocketMessageType {
    MAP_UPDATE, MAP_DELETE
}
data class WebsocketMessage(val type: WebsocketMessageType, val msg: Any)

fun Route.mapsWebsocket() {
    val activeChannels = mutableListOf<Channel<String>>()

    application.rabbitOptional {
        consumeAck("bm.updateStream", Int::class) { _, mapId ->
            transaction {
                Beatmap
                    .joinVersions(true)
                    .joinUploader()
                    .joinCurator()
                    .select {
                        Beatmap.id eq mapId
                    }
                    .complexToBeatmap()
                    .firstOrNull()
            }?.let { map ->
                if (map.deletedAt == null) {
                    activeChannels.forEach { it.send(inlineJackson.writeValueAsString(WebsocketMessage(WebsocketMessageType.MAP_UPDATE, MapDetail.from(map)))) }
                } else {
                    activeChannels.forEach { it.send(inlineJackson.writeValueAsString(WebsocketMessage(WebsocketMessageType.MAP_DELETE, mapId))) }
                }
            }
        }
    }

    webSocket("/ws/maps") {
        Channel<String>(10).also {
            activeChannels.add(it)
        }.let { channel ->
            try {
                launch {
                    channel.consumeEach {
                        outgoing.send(Frame.Text(it))
                    }
                }

                incoming.consumeEach {
                    // This will block while the socket is open
                    // When closed the finally automatically shuts everything down
                    //println("Received: $it")
                }
            } finally {
                activeChannels.remove(channel)
                channel.close()
            }
        }
    }
}