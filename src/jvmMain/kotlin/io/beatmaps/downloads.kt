package io.beatmaps

import io.beatmaps.common.consumeAck
import io.beatmaps.common.db.incrementBy
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.rabbitOptional
import io.beatmaps.controllers.DownloadInfo
import io.ktor.application.Application
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

fun Application.downloadsThread() {
    rabbitOptional {
        consumeAck("bm.downloadCount", DownloadInfo::class) { _, dl ->
            transaction {
                Beatmap.join(Versions, JoinType.INNER, onColumn = Beatmap.id, Versions.mapId).update({ Versions.hash eq dl.hash }) {
                    it[Beatmap.downloads] = incrementBy(Beatmap.downloads, 1)
                }
            }
        }
    }
}