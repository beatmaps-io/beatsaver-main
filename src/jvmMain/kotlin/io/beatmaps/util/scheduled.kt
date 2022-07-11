package io.beatmaps.util

import io.beatmaps.common.api.EMapState
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.dbo.VersionsDao
import io.beatmaps.common.rabbitOptional
import io.ktor.application.Application
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import pl.jutupe.ktor_rabbitmq.RabbitMQ
import pl.jutupe.ktor_rabbitmq.publish
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.Timer
import java.util.TimerTask
import java.util.logging.Level
import java.util.logging.Logger

private val schedulerLogger = Logger.getLogger("bmio.Scheduler")

fun Application.scheduleTask() {
    val firstInvocationLocal = LocalDateTime.now().withSecond(0).plusMinutes(1L)
    val firstInvocation = Timestamp.valueOf(firstInvocationLocal)

    schedulerLogger.info("Scheduler check rabbitmq")
    rabbitOptional {
        schedulerLogger.info("Scheduler starting")

        val t = Timer()
        t.scheduleAtFixedRate(CheckScheduled(this), firstInvocation, 60 * 1000L)
    }
}

class CheckScheduled(private val rb: RabbitMQ) : TimerTask() {
    override fun run() {
        try {
            transaction {
                VersionsDao.wrapRows(
                    Versions.select {
                        Versions.state eq EMapState.Scheduled and (Versions.scheduledAt lessEq NowExpression(Versions.scheduledAt))
                    }
                ).mapNotNull {
                    schedulerLogger.info("Scheduler publishing ${it.hash}")
                    if (publishVersion(it.mapId.value, it.hash)) it else null
                }
            }.forEach {
                rb.publish("beatmaps", "maps.${it.mapId.value}.updated", null, it.mapId.value)
            }
        } catch (e: Exception) {
            schedulerLogger.log(Level.SEVERE, "Exception while running task", e)
        }
    }
}
