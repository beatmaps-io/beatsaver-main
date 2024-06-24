package io.beatmaps.util

import io.beatmaps.common.api.EMapState
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.dbo.VersionsDao
import io.beatmaps.common.rabbitOptional
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.Application
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
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
                    Versions.selectAll().where {
                        Versions.state eq EMapState.Scheduled and (Versions.scheduledAt lessEq NowExpression(Versions.scheduledAt))
                    }
                ).mapNotNull {
                    schedulerLogger.info { "Scheduler publishing ${it.hash}" }
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
