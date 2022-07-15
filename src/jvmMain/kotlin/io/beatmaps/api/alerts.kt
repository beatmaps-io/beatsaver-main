package io.beatmaps.api

import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.dbo.Alert
import io.beatmaps.common.dbo.AlertDao
import io.beatmaps.common.dbo.AlertRecipient
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.locations.post
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.sessions.sessions
import io.ktor.sessions.set
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

    @Location("/markall")
    data class MarkAll(val api: AlertsApi)
}

data class MarkAlert(val id: Int, val read: Boolean)

data class MarkAllAlerts(val read: Boolean)

fun alertCount(userId: Int) = AlertRecipient
    .select {
        (AlertRecipient.recipientId eq userId) and
            AlertRecipient.readAt.isNull()
    }.count().toInt()

fun Route.alertsRoute() {
    fun getAlerts(userId: Int, read: Boolean): List<UserAlert> = transaction {
        AlertRecipient
            .join(Alert, JoinType.LEFT, AlertRecipient.alertId, Alert.id)
            .select {
                (AlertRecipient.recipientId eq userId) and
                    AlertRecipient.readAt.run { if (read) isNotNull() else isNull() }
            }
            .orderBy(Alert.sentAt)
            .map {
                AlertDao.wrapRow(it).let { alert ->
                    UserAlert(alert.id.value, alert.head, alert.body, alert.type, alert.sentAt.toKotlinInstant())
                }
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
                        AlertRecipient.readAt.run { if (req.read) isNull() else isNotNull() } and
                        (AlertRecipient.recipientId eq user.userId)
                }) {
                    if (req.read) {
                        it[readAt] = NowExpression(readAt.columnType)
                    } else {
                        it[readAt] = null
                    }
                } > 0
            }

            if (result) {
                transaction {
                    call.sessions.set(user.copy(alerts = alertCount(user.userId)))
                }
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.BadRequest)
            }
        }
    }

    post<AlertsApi.MarkAll> {
        val req = call.receive<MarkAllAlerts>()

        requireAuthorization { user ->
            val result = transaction {
                AlertRecipient.update({
                    AlertRecipient.readAt.run { if (req.read) isNull() else isNotNull() } and
                        (AlertRecipient.recipientId eq user.userId)
                }) {
                    if (req.read) {
                        it[readAt] = NowExpression(readAt.columnType)
                    } else {
                        it[readAt] = null
                    }
                }
            } > 0

            if (result) {
                call.sessions.set(user.copy(alerts = 0))
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.BadRequest)
            }
        }
    }
}
