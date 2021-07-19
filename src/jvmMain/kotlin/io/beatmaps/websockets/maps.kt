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

fun Route.mapsWebsocket() {
    val activeChannels = mutableListOf<Channel<String>>()

    application.rabbitOptional {
        consumeAck("bm.updateStream", Int::class) { _, mapId ->
            transaction {
                Beatmap
                    //.joinVersions(true, null) // Allow returning non-published versions
                    .joinVersions(true)
                    .joinUploader()
                    .joinCurator()
                    .select {
                        Beatmap.id eq mapId and (Beatmap.deletedAt.isNull())
                    }
                    .complexToBeatmap()
                    .firstOrNull()
                    //?.enrichTestplays()
                    ?.run {
                        MapDetail.from(this)
                    }
            }?.let { map ->
                activeChannels.forEach { it.send(inlineJackson.writeValueAsString(map)) }
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