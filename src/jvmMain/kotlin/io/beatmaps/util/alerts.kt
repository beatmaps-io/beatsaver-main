package io.beatmaps.util

import io.beatmaps.api.alertCount
import io.beatmaps.common.amqp.consumeAck
import io.beatmaps.common.amqp.rabbitOptional
import io.beatmaps.common.amqp.rb
import io.beatmaps.login.MongoClient
import io.beatmaps.login.Session
import io.ktor.server.application.Application
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.builtins.serializer
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import pl.jutupe.ktor_rabbitmq.RabbitMQInstance
import pl.jutupe.ktor_rabbitmq.publish

fun updateAlertCount(rb: RabbitMQInstance?, userId: Int) {
    if (rb == null) return

    TransactionManager.currentOrNull()?.commit()
    rb.publish("beatmaps", "user.alerts.$userId", null, userId)
}

fun updateAlertCount(rb: RabbitMQInstance?, ids: List<Int>) = ids.map { updateAlertCount(rb, it) }
fun RoutingContext.updateAlertCount(ids: List<Int>) = ids.map { updateAlertCount(it) }
fun RoutingContext.updateAlertCount(userId: Int) = updateAlertCount(call.rb(), userId)

fun Application.alertsThread() {
    rabbitOptional {
        consumeAck("bm.alertCount", Int.serializer()) { _, userId ->
            val alertCount = transaction {
                alertCount(userId)
            }

            MongoClient.updateSessions(userId, Session::alerts, alertCount)
        }
    }
}
