package io.beatmaps.util

import io.beatmaps.common.consumeAck
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Playlist
import io.beatmaps.common.dbo.PlaylistMap
import io.beatmaps.common.dbo.PlaylistMapDao
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.common.dbo.joinVersions
import io.beatmaps.common.rabbitOptional
import io.ktor.server.application.Application
import kotlinx.serialization.builtins.serializer
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import pl.jutupe.ktor_rabbitmq.publish
import java.math.BigDecimal

fun Application.playlistStats() {
    rabbitOptional {
        consumeAck("bm.mapPlaylistTrigger", Int.serializer()) { _, mapId ->
            transaction {
                PlaylistMapDao.wrapRows(
                    PlaylistMap.selectAll()
                        .where {
                            PlaylistMap.mapId eq mapId
                        }
                ).toList()
            }.forEach {
                publish("beatmaps", "playlists.${it.playlistId}.stats", null, it.playlistId.value)
            }
        }

        consumeAck("bm.playlistStats", Int.serializer()) { _, playlistId ->
            transaction {
                val beatmaps = Beatmap
                    .joinVersions()
                    .join(PlaylistMap, JoinType.INNER, Beatmap.id, PlaylistMap.mapId)
                    .selectAll()
                    .where {
                        PlaylistMap.playlistId eq playlistId and Beatmap.deletedAt.isNull()
                    }
                    .complexToBeatmap()

                val totalMapsVal = beatmaps.size
                val minNpsVal = beatmaps.minOfOrNull { it.minNps } ?: BigDecimal.ZERO
                val maxNpsVal = beatmaps.maxOfOrNull { it.maxNps } ?: BigDecimal.ZERO

                Playlist.update({
                    Playlist.id eq playlistId
                }) {
                    it[totalMaps] = totalMapsVal
                    it[minNps] = minNpsVal
                    it[maxNps] = maxNpsVal
                }
            }
        }
    }
}
