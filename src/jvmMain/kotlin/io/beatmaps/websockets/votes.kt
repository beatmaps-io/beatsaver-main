package io.beatmaps.websockets

import io.beatmaps.api.MapDetail
import io.beatmaps.api.VoteSummaryHex
import io.beatmaps.api.from
import io.beatmaps.common.consumeAck
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.common.inlineJackson
import io.beatmaps.common.rabbitOptional
import io.ktor.routing.Route
import io.ktor.routing.application
import io.ktor.websocket.webSocket
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.lang.Integer.toHexString

fun Route.votesWebsocket() {
    val holder = ChannelHolder()

    application.rabbitOptional {
        consumeAck("bm.voteStream", Int::class) { _, mapId ->
            transaction {
                Beatmap
                    .joinVersions(false)
                    .select {
                        (Beatmap.id eq mapId) and Beatmap.deletedAt.isNull()
                    }
                    .complexToBeatmap()
                    .firstOrNull()?.let {
                        val mapDetail = MapDetail.from(it)

                        VoteSummaryHex(
                            mapDetail.publishedVersion()?.hash,
                            toHexString(it.id.value),
                            it.upVotesInt,
                            it.downVotesInt,
                            it.score.toDouble()
                        )
                    }
            }?.let { summary ->
                loopAndTerminateOnError(holder) {
                    it.send(inlineJackson.writeValueAsString(WebsocketMessage(WebsocketMessageType.VOTE, summary)))
                }
            }
        }
    }

    webSocket("/ws/votes") {
        websocketConnection(holder)
    }
}