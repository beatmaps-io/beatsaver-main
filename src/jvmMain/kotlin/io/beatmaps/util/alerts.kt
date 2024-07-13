package io.beatmaps.util

import io.beatmaps.api.alertCount
import io.beatmaps.common.consumeAck
import io.beatmaps.common.rabbitOptional
import io.beatmaps.common.rb
import io.beatmaps.login.MongoClient
import io.beatmaps.login.MongoSession
import io.beatmaps.login.Session
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.util.pipeline.PipelineContext
import kotlinx.serialization.builtins.serializer
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.litote.kmongo.div
import org.litote.kmongo.eq
import org.litote.kmongo.setValue
import pl.jutupe.ktor_rabbitmq.RabbitMQInstance
import pl.jutupe.ktor_rabbitmq.publish

fun updateAlertCount(rb: RabbitMQInstance?, userId: Int) {
    if (rb == null) return

    TransactionManager.currentOrNull()?.commit()
    rb.publish("beatmaps", "user.alerts.$userId", null, userId)
}

fun updateAlertCount(rb: RabbitMQInstance?, ids: List<Int>) = ids.map { updateAlertCount(rb, it) }
fun PipelineContext<*, ApplicationCall>.updateAlertCount(ids: List<Int>) = ids.map { updateAlertCount(it) }
fun PipelineContext<*, ApplicationCall>.updateAlertCount(userId: Int) = updateAlertCount(call.rb(), userId)

fun Application.alertsThread() {
    rabbitOptional {
        consumeAck("bm.alertCount", Int.serializer()) { _, userId ->
            val alertCount = transaction {
                alertCount(userId)
            }

            if (MongoClient.connected) {
                MongoClient.sessions.updateMany(MongoSession::session / Session::userId eq userId, setValue(MongoSession::session / Session::alerts, alertCount))
            }
        }
    }
}
