package io.beatmaps

import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Downloads
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.db.updateReturning
import io.beatmaps.common.es
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.hours
import kotlin.time.minutes

fun downloadsThread() {
    GlobalScope.launch(es.asCoroutineDispatcher()) {
        scheduleRepeating(1.hours) {
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

fun incrementBy(column: Column<Int>, num: Int = 1) = object: Expression<Int>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder.append("${TransactionManager.current().identity(column)} + $num")
    }
}

suspend inline fun scheduleRepeating(
    interval: Duration,
    startInterval: Duration = 5.minutes,
    crossinline action: () -> Unit
) {
    delay(startInterval)
    while(coroutineContext.isActive) {
        action()
        delay(interval)
    }
}