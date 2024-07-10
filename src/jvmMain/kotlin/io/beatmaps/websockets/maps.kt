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
import io.beatmaps.common.jsonWithDefaults
import io.beatmaps.common.rabbitOptional
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import pl.jutupe.ktor_rabbitmq.publish
import java.lang.Integer.toHexString

enum class MapUpdateMessageType {
    MAP_UPDATE, MAP_DELETE
}

@Serializable
data class MapUpdateMessage(val type: MapUpdateMessageType, val msg: JsonElement)

fun Route.mapUpdateEnricher() {
    application.rabbitOptional {
        consumeAck("bm.updateStream", Int.serializer()) { _, mapId ->
            transaction {
                Beatmap
                    .joinVersions(true, state = null)
                    .joinUploader()
                    .joinCurator()
                    .selectAll()
                    .where {
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
                    val subJson = jsonWithDefaults.encodeToJsonElement(map.second)
                    MapUpdateMessage(MapUpdateMessageType.MAP_UPDATE, subJson)
                } else {
                    val subJson = jsonWithDefaults.encodeToJsonElement(toHexString(mapId))
                    MapUpdateMessage(MapUpdateMessageType.MAP_DELETE, subJson)
                }

                publish("beatmaps", "ws.map.$mapId", null, wsMsg)
            }
        }
    }
}
