package io.beatmaps.api

import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.dbo.AlertRecipient
import io.beatmaps.common.dbo.Alert
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.locations.options
import io.ktor.locations.post
import io.ktor.request.receive
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.routing.Route
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

@Location("/api/alerts") class AlertsApi {
    @Location("/unread/{id?}")
    data class Unread(val id: Int? = null, val api: AlertsApi)

    @Location("/read/{id?}")
    data class Read(val id: Int? = null, val api: AlertsApi)

    @Location("/mark")
    data class Mark(val api: AlertsApi)
}

data class MarkAlert(val id: Int, val read: Boolean)

fun Route.alertsRoute() {
    options<AlertsApi.Unread> {
        call.response.header("Access-Control-Allow-Origin", "*")
        call.respond(HttpStatusCode.OK)
    }

    options<AlertsApi.Read> {
        call.response.header("Access-Control-Allow-Origin", "*")
        call.respond(HttpStatusCode.OK)
    }

    fun getAlerts(userId: Int, read: Boolean): List<AlertV2> = transaction {
        AlertRecipient
            .join(Alert, JoinType.LEFT, AlertRecipient.alertId, Alert.id)
            .select {
                (AlertRecipient.recipientId eq userId) and
                AlertRecipient.readAt.run { if (read) isNotNull() else isNull() }
            }
            .orderBy(Alert.sentAt)
            .map {
                AlertV2(it[Alert.head], it[Alert.body], it[Alert.type], it[Alert.sentAt].toKotlinInstant())
            }
    }

    get<AlertsApi.Unread> {
        requireAuthorization { user ->
            val targetId = if (it.id != null && user.isAdmin()) {
                it.id
            } else {
                user.userId
            }

            val alerts = getAlerts(targetId, false)

            call.respond(alerts)
        }
    }

    get<AlertsApi.Read> {
        requireAuthorization { user ->
            val targetId = if (it.id != null && user.isAdmin()) {
                it.id
            } else {
                user.userId
            }

            val alerts = getAlerts(targetId, true)

            call.respond(alerts)
        }
    }

    post<AlertsApi.Mark> {
        val req = call.receive<MarkAlert>()

        requireAuthorization { user ->
            val result = transaction {
                AlertRecipient.update({
                    (AlertRecipient.id eq req.id) and
                    (AlertRecipient.recipientId eq user.userId)
                }) {
                    if (req.read) {
                        it[readAt] = NowExpression(readAt.columnType)
                    }
                    else {
                        it[readAt] = null
                    }
                } > 0
            }

            call.respond(if (result) HttpStatusCode.OK else HttpStatusCode.BadRequest)
        }
    }
}
