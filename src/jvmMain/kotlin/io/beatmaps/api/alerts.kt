package io.beatmaps.api

import io.beatmaps.common.api.EAlertType
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.dbo.Alert
import io.beatmaps.common.dbo.AlertDao
import io.beatmaps.common.dbo.AlertRecipient
import io.beatmaps.login.Session
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.locations.Location
import io.ktor.server.locations.get
import io.ktor.server.locations.post
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.util.pipeline.PipelineContext
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

@Location("/api/alerts") class AlertsApi {
    @Location("/unread/{page?}")
    data class Unread(val page: Long? = null, val type: String? = null, val api: AlertsApi)

    @Location("/read/{page?}")
    data class Read(val page: Long? = null, val type: String? = null, val api: AlertsApi)

    @Location("/stats")
    data class Stats(val api: AlertsApi)

    @Location("/mark")
    data class Mark(val api: AlertsApi)

    @Location("/markall")
    data class MarkAll(val api: AlertsApi)
}

fun alertCount(userId: Int) = AlertRecipient
    .select {
        (AlertRecipient.recipientId eq userId) and
            AlertRecipient.readAt.isNull()
    }.count().toInt()

fun Route.alertsRoute() {
    fun getAlerts(userId: Int, read: Boolean, page: Long? = null, type: List<EAlertType>? = null): List<UserAlert> = transaction {
        AlertRecipient
            .join(Alert, JoinType.LEFT, AlertRecipient.alertId, Alert.id)
            .select {
                (AlertRecipient.recipientId eq userId) and
                    AlertRecipient.readAt.run { if (read) isNotNull() else isNull() } and
                    if (type != null) { Alert.type.inList(type) } else Op.TRUE
            }
            .orderBy(Alert.sentAt, SortOrder.DESC)
            .limit(page)
            .map {
                AlertDao.wrapRow(it).let { alert ->
                    UserAlert(alert.id.value, alert.head, alert.body, alert.type, alert.sentAt.toKotlinInstant())
                }
            }
    }

    get<AlertsApi.Unread> {
        requireAuthorization { user ->
            val alerts = getAlerts(user.userId, false, it.page, EAlertType.fromList(it.type))

            call.respond(alerts)
        }
    }

    get<AlertsApi.Read> {
        requireAuthorization { user ->
            val alerts = getAlerts(user.userId, true, it.page, EAlertType.fromList(it.type))

            call.respond(alerts)
        }
    }

    fun getStats(userId: Int) = AlertRecipient
        .join(Alert, JoinType.LEFT, AlertRecipient.alertId, Alert.id)
        .slice(AlertRecipient.id.count(), AlertRecipient.readAt.isNull(), Alert.type)
        .select {
            (AlertRecipient.recipientId eq userId)
        }
        .groupBy(Alert.type, AlertRecipient.readAt.isNull())
        .map {
            StatPart(
                it[Alert.type],
                !it[AlertRecipient.readAt.isNull()],
                it[AlertRecipient.id.count()]
            )
        }

    get<AlertsApi.Stats> {
        requireAuthorization { user ->
            val statParts = transaction { getStats(user.userId) }

            call.respond(UserAlertStats.fromParts(statParts))
        }
    }

    suspend fun PipelineContext<*, ApplicationCall>.respondStats(user: Session, stats: List<StatPart>?) =
        if (stats != null) {
            val unread = stats.filter { !it.isRead }.sumOf { it.count }.toInt()

            call.sessions.set(user.copy(alerts = unread))
            call.respond(UserAlertStats.fromParts(stats))
        } else {
            call.respond(HttpStatusCode.BadRequest)
        }

    post<AlertsApi.Mark> {
        val req = call.receive<AlertUpdate>()

        requireAuthorization { user ->
            val stats = transaction {
                val result = AlertRecipient
                    .join(Alert, JoinType.INNER, AlertRecipient.alertId, Alert.id)
                    .update({
                        (Alert.id eq req.id) and
                            AlertRecipient.readAt.run { if (req.read) isNull() else isNotNull() } and
                            (AlertRecipient.recipientId eq user.userId)
                    }) {
                        if (req.read) {
                            it[AlertRecipient.readAt] = NowExpression(AlertRecipient.readAt.columnType)
                        } else {
                            it[AlertRecipient.readAt] = null
                        }
                    }

                if (result > 0) {
                    getStats(user.userId)
                } else null
            }

            respondStats(user, stats)
        }
    }

    post<AlertsApi.MarkAll> {
        val req = call.receive<AlertUpdateAll>()

        requireAuthorization { user ->
            val stats = transaction {
                val result = AlertRecipient.update({
                    AlertRecipient.readAt.run { if (req.read) isNull() else isNotNull() } and
                        (AlertRecipient.recipientId eq user.userId)
                }) {
                    if (req.read) {
                        it[readAt] = NowExpression(readAt.columnType)
                    } else {
                        it[readAt] = null
                    }
                }

                if (result > 0) {
                    getStats(user.userId)
                } else null
            }

            respondStats(user, stats)
        }
    }
}
