package io.beatmaps.websockets

import io.beatmaps.api.MapDetail
import io.beatmaps.api.from
import io.beatmaps.common.CDNUpdate
import io.beatmaps.common.consumeAck
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.common.dbo.joinCurator
import io.beatmaps.common.dbo.joinUploader
import io.beatmaps.common.dbo.joinVersions
import io.beatmaps.common.rabbitOptional
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import pl.jutupe.ktor_rabbitmq.publish
import java.lang.Integer.toHexString

enum class MapUpdateMessageType {
    MAP_UPDATE, MAP_DELETE
}
data class MapUpdateMessage(val type: MapUpdateMessageType, val msg: Any)

fun Route.mapUpdateEnricher() {
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

                val wsMsg = if (map.first == null) {
                    MapUpdateMessage(MapUpdateMessageType.MAP_UPDATE, map.second)
                } else {
                    MapUpdateMessage(MapUpdateMessageType.MAP_DELETE, toHexString(mapId))
                }

                publish("beatmaps", "ws.map.$mapId", null, wsMsg)
            }
        }
    }
}
