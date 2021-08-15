package io.beatmaps

import io.beatmaps.common.db.incrementBy
import io.beatmaps.common.db.updateReturning
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Downloads
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.es
import kotlinx.coroutines.*
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration

fun downloadsThread() {
    GlobalScope.launch(es.asCoroutineDispatcher()) {
        scheduleRepeating(Duration.hours(1)) {
            transaction {
                Downloads.updateReturning({
                        Downloads.processed eq false
                    }, {
                        it[processed] = true
                    }, Downloads.hash
                )?.let { result ->
                    val downloads = result.groupingBy { it[Downloads.hash] }.eachCount()
                    val maps = Versions.select {
                        Versions.hash inList downloads.keys
                    }.toList().associateBy({ it[Versions.hash] }, { it[Versions.mapId] })

                    downloads.forEach { dl ->
                        maps[dl.key]?.let { mapId ->
                            Beatmap.update({ Beatmap.id eq mapId }) {
                                it[Beatmap.downloads] = incrementBy(Beatmap.downloads, dl.value)
                            }
                        }
                    }
                }
            }
        }
    }
}

suspend inline fun scheduleRepeating(
    interval: Duration,
    startInterval: Duration = Duration.minutes(5),
    crossinline action: () -> Unit
) {
    delay(startInterval)
    while(coroutineContext.isActive) {
        action()
        delay(interval)
    }
}