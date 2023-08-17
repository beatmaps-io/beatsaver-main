package io.beatmaps.api

import io.beatmaps.common.api.EAlertType
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.dbo.Alert
import io.beatmaps.common.dbo.AlertDao
import io.beatmaps.common.dbo.AlertRecipient
import io.beatmaps.common.dbo.Collaboration
import io.beatmaps.common.dbo.CollaborationDAO
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
import org.jetbrains.exposed.sql.not
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.lang.Integer.toHexString

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
    .join(Collaboration, JoinType.FULL) { Op.FALSE }
    .select {
        (AlertRecipient.recipientId eq userId and AlertRecipient.readAt.isNull()) or
            (Collaboration.collaboratorId eq userId and not(Collaboration.accepted))
    }.count().toInt()

fun UserAlert.Companion.from(collaboration: CollaborationDAO) = UserAlert(
    head = "Collaboration Proposal",
    body = "You have been invited to be a collaborator on #${toHexString(collaboration.mapId.value)}. " +
        "By accepting this proposal, your name will appear on the map's info screen, " +
        "and the map will appear on your account.",
    type = EAlertType.Collaboration,
    time = collaboration.requestedAt.toKotlinInstant(),
    collaborationId = collaboration.id.value
)

fun Route.alertsRoute() {
    fun getAlerts(userId: Int, read: Boolean, page: Long? = null, type: List<EAlertType>? = null): List<UserAlert> = transaction {
        AlertRecipient
            .join(Alert, JoinType.LEFT, AlertRecipient.alertId, Alert.id)
            .join(Collaboration, JoinType.FULL) { Op.FALSE }
            .select {
                (
                    AlertRecipient.recipientId eq userId and
                        AlertRecipient.readAt.run { if (read) isNotNull() else isNull() } or
                        if (!read) (Collaboration.collaboratorId eq userId and not(Collaboration.accepted)) else Op.FALSE
                    ) and when {
                    type == null -> Op.TRUE
                    type.contains(EAlertType.Collaboration) -> Alert.type.inList(type) or Collaboration.id.isNotNull()
                    else -> Alert.type.inList(type)
                }
            }
            .orderBy(Alert.sentAt, SortOrder.DESC)
            .limit(page)
            .mapNotNull {
                if (it.getOrNull(Alert.id) != null) {
                    AlertDao.wrapRow(it).let { alert ->
                        UserAlert(alert.id.value, alert.head, alert.body, alert.type, alert.sentAt.toKotlinInstant())
                    }
                } else if (it.getOrNull(Collaboration.id) != null) {
                    UserAlert.from(CollaborationDAO.wrapRow(it))
                } else null
            }
    }

    get<AlertsApi.Unread> {
        requireAuthorization(OauthScope.ALERTS) { user ->
            val alerts = getAlerts(user.userId, false, it.page, EAlertType.fromList(it.type))

            call.respond(alerts)
        }
    }

    get<AlertsApi.Read> {
        requireAuthorization(OauthScope.ALERTS) { user ->
            val alerts = getAlerts(user.userId, true, it.page, EAlertType.fromList(it.type))

            call.respond(alerts)
        }
    }

    fun getStats(userId: Int) = AlertRecipient
        .join(Alert, JoinType.LEFT, AlertRecipient.alertId, Alert.id)
        .join(Collaboration, JoinType.FULL) { Op.FALSE }
        .slice(AlertRecipient.id.count(), AlertRecipient.readAt.isNull(), Alert.type, Collaboration.id.count())
        .select {
            (AlertRecipient.recipientId eq userId) or
                (Collaboration.collaboratorId eq userId and not(Collaboration.accepted))
        }
        .groupBy(Alert.type, AlertRecipient.readAt.isNull())
        .mapNotNull {
            if (it.getOrNull(Alert.type) != null) {
                StatPart(
                    it[Alert.type],
                    !it[AlertRecipient.readAt.isNull()],
                    it[AlertRecipient.id.count()]
                )
            } else if (it.getOrNull(Collaboration.id.count()) != null) {
                StatPart(
                    EAlertType.Collaboration,
                    false,
                    it[Collaboration.id.count()]
                )
            } else null
        }

    get<AlertsApi.Stats> {
        requireAuthorization(OauthScope.ALERTS) { user ->
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

        requireAuthorization(OauthScope.MARK_ALERTS) { user ->
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

        requireAuthorization(OauthScope.MARK_ALERTS) { user ->
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
