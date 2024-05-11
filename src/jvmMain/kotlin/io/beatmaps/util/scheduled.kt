package io.beatmaps.util

import io.beatmaps.common.api.EMapState
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.dbo.VersionsDao
import io.beatmaps.common.rabbitOptional
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.Application
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import pl.jutupe.ktor_rabbitmq.RabbitMQInstance
import pl.jutupe.ktor_rabbitmq.publish
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.Timer
import java.util.TimerTask

class CheckScheduled(private val rb: RabbitMQInstance) : TimerTask() {
    override fun run() {
        try {
            transaction {
                VersionsDao.wrapRows(
                    Versions.select {
                        Versions.state eq EMapState.Scheduled and (Versions.scheduledAt lessEq NowExpression(Versions.scheduledAt))
                    }
                ).mapNotNull {
                    schedulerLogger.info { "Scheduler publishing ${it.hash}" }
                    runBlocking { delay(1L) }
                    // Undeceiver: I am not adding the "alert on update" parameter to the database because it feels like too much effort for
                    // the extremely unlikely case that someone published a map, then unpublished it, then republished it, but scheduled it for release,
                    // and also doesn't want the alert to go out. If this ever happens, the alert will go out regardless (if it's scheduled it's likely it's a big deal).
                    // The parameter can be passed here no problems, but then we'd have to save it in the database when scheduling.
                    if (publishVersion(it.mapId.value, it.hash, true, rb)) it else null
                }
            }.forEach {
                rb.publish("beatmaps", "maps.${it.mapId.value}.updated.state", null, it.mapId.value)
            }
        } catch (e: Exception) {
            schedulerLogger.error(e) { "Exception while running task" }
        }
    }

    companion object {
        private val schedulerLogger = KotlinLogging.logger {}

        fun Application.scheduleTask() {
            val firstInvocationLocal = LocalDateTime.now().withSecond(0).plusMinutes(1L)
            val firstInvocation = Timestamp.valueOf(firstInvocationLocal)

            schedulerLogger.info { "Scheduler check rabbitmq" }
            rabbitOptional {
                schedulerLogger.info { "Scheduler starting" }

                val t = Timer("Map Publish")
                t.scheduleAtFixedRate(CheckScheduled(this), firstInvocation, 60 * 1000L)
            }
        }
    }
}
