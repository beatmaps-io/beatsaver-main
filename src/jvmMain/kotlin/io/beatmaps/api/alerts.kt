package io.beatmaps.api

import io.beatmaps.common.api.EAlertType
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.dbo.Alert
import io.beatmaps.common.dbo.AlertDao
import io.beatmaps.common.dbo.AlertRecipient
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Collaboration
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.UserDao
import io.beatmaps.login.Session
import io.beatmaps.util.cdnPrefix
import io.beatmaps.util.requireAuthorization
import io.beatmaps.util.updateAlertCount
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.locations.Location
import io.ktor.server.locations.get
import io.ktor.server.locations.post
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.util.pipeline.PipelineContext
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.intLiteral
import org.jetbrains.exposed.sql.not
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.unionAll
import org.jetbrains.exposed.sql.update
import java.lang.Integer.toHexString

@Location("/api/alerts")
class AlertsApi {
    @Location("/unread/{page?}")
    data class Unread(val page: Long? = null, val type: String? = null, val api: AlertsApi)

    @Location("/read/{page?}")
    data class Read(val page: Long? = null, val type: String? = null, val api: AlertsApi)

    @Location("/stats")
    data class Stats(val api: AlertsApi)

    @Location("/options")
    data class Options(val api: AlertsApi)

    @Location("/mark")
    data class Mark(val api: AlertsApi)

    @Location("/markall")
    data class MarkAll(val api: AlertsApi)
}

val ac = AlertRecipient.id.count().alias("ac")
val cc = Collaboration.collaboratorId.count().alias("cc")
fun alertCount(userId: Int): Int {
    val alertCount = AlertRecipient
        .select(ac)
        .where {
            AlertRecipient.recipientId eq userId and AlertRecipient.readAt.isNull()
        }
        .alias("a")

    val collabCount = Collaboration
        .select(cc)
        .where {
            Collaboration.collaboratorId eq userId and not(Collaboration.accepted)
        }
        .alias("c")

    return alertCount
        .join(collabCount, JoinType.LEFT, additionalConstraint = { Op.TRUE })
        .select(alertCount[ac], collabCount[cc])
        .firstOrNull()?.let {
            it[alertCount[ac]] + it[collabCount[cc]]
        }?.toInt() ?: 0
}

fun UserAlert.Companion.from(collaboration: CollaborationDetail) = collaboration.map?.let { map ->
    UserAlert(
        head = "Collaboration Proposal",
        body = "@${map.uploader.name} has invited you to be a collaborator on #${toHexString(collaboration.mapId)}: **${map.name}**. " +
            "By accepting this proposal, your name will appear on the map's info screen, " +
            "and the map will appear on your account.",
        type = EAlertType.Collaboration,
        time = collaboration.requestedAt,
        collaborationId = collaboration.id
    )
}

fun Route.alertsRoute() {
    fun PipelineContext<*, ApplicationCall>.getAlerts(userId: Int, read: Boolean, page: Long? = null, type: List<EAlertType>? = null): List<UserAlert> = transaction {
        val (s0, s1) = intLiteral(0).alias("s") to intLiteral(1).alias("s")

        val collabQuery = Collaboration
            .select(s0, Collaboration.id, Collaboration.requestedAt)
            .where {
                Collaboration.collaboratorId eq userId and not(Collaboration.accepted)
            }

        val alertQuery = AlertRecipient
            .join(Alert, JoinType.LEFT, AlertRecipient.alertId, Alert.id)
            .select(s1, AlertRecipient.alertId, Alert.sentAt)
            .where {
                AlertRecipient.recipientId eq userId and AlertRecipient.readAt.run { if (read) isNotNull() else isNull() } and
                    if (type != null) { Alert.type.inList(type) } else Op.TRUE
            }

        if (!read && (type == null || type.contains(EAlertType.Collaboration))) {
            alertQuery.unionAll(collabQuery)
        } else {
            alertQuery
        }.alias("u").let { u ->
            u
                .join(Alert, JoinType.LEFT, u[AlertRecipient.alertId], Alert.id) { u[s1] eq intLiteral(1) }
                .join(Collaboration, JoinType.LEFT, u[AlertRecipient.alertId], Collaboration.id) { u[s1] eq intLiteral(0) }
                .join(Beatmap, JoinType.LEFT, Beatmap.id, Collaboration.mapId)
                .join(User, JoinType.LEFT, User.id, Beatmap.uploader)
                .selectAll()
                .orderBy(u[Alert.sentAt] to SortOrder.DESC)
                .limit(page)
                .mapNotNull {
                    if (it.getOrNull(Alert.id) != null) {
                        AlertDao.wrapRow(it).let { alert ->
                            UserAlert(alert.id.value, alert.head, alert.body, alert.type, alert.sentAt.toKotlinInstant())
                        }
                    } else if (it.getOrNull(Collaboration.id) != null) {
                        UserAlert.from(CollaborationDetail.from(it, cdnPrefix()))
                    } else { null }
                }
        }
    }

    get<AlertsApi.Unread> {
        requireAuthorization(OauthScope.ALERTS) { _, user ->
            val alerts = getAlerts(user.userId, false, it.page, EAlertType.fromList(it.type))

            call.respond(alerts)
        }
    }

    get<AlertsApi.Read> {
        requireAuthorization(OauthScope.ALERTS) { _, user ->
            val alerts = getAlerts(user.userId, true, it.page, EAlertType.fromList(it.type))

            call.respond(alerts)
        }
    }

    fun getStats(userId: Int) =
        AlertRecipient
            .join(Alert, JoinType.LEFT, AlertRecipient.alertId, Alert.id)
            .select(AlertRecipient.id.count(), AlertRecipient.readAt.isNull(), Alert.type)
            .where {
                (AlertRecipient.recipientId eq userId)
            }
            .groupBy(Alert.type, AlertRecipient.readAt.isNull())
            .unionAll(
                Collaboration
                    .select(Collaboration.id.count(), Op.nullOp<Boolean>(), Op.nullOp<EAlertType>())
                    .where {
                        Collaboration.collaboratorId eq userId and not(Collaboration.accepted)
                    }
            ).map {
                if (it.getOrNull(Alert.type) != null) {
                    StatPart(
                        it[Alert.type],
                        !it[AlertRecipient.readAt.isNull()],
                        it[AlertRecipient.id.count()]
                    )
                } else {
                    StatPart(
                        EAlertType.Collaboration,
                        false,
                        it[AlertRecipient.id.count()]
                    )
                }
            }

    get<AlertsApi.Stats> {
        requireAuthorization(OauthScope.ALERTS) { _, sess ->
            val (statParts, user) = transaction { getStats(sess.userId) to UserDao[sess.userId] }

            call.respond(UserAlertStats.fromParts(statParts).copy(reviewAlerts = user.reviewAlerts, curationAlerts = user.curationAlerts, followAlerts = user.followAlerts))
        }
    }

    post<AlertsApi.Options> {
        requireAuthorization { _, sess ->
            val req = call.receive<AlertOptionsRequest>()

            transaction {
                User.update({
                    User.id eq sess.userId
                }) {
                    it[reviewAlerts] = req.reviewAlerts
                    it[curationAlerts] = req.curationAlerts
                    it[followAlerts] = req.followAlerts
                }
            }

            call.respond(ActionResponse.success())
        }
    }

    suspend fun PipelineContext<*, ApplicationCall>.respondStats(sess: Session, stats: List<StatPart>?) =
        if (stats != null) {
            val user = transaction { UserDao[sess.userId] }

            updateAlertCount(sess.userId)
            call.respond(UserAlertStats.fromParts(stats).copy(reviewAlerts = user.reviewAlerts, curationAlerts = user.curationAlerts, followAlerts = user.followAlerts))
        } else {
            call.respond(HttpStatusCode.BadRequest)
        }

    post<AlertsApi.Mark> {
        val req = call.receive<AlertUpdate>()

        requireAuthorization(OauthScope.MARK_ALERTS) { _, user ->
            val stats = transaction {
                val result = AlertRecipient
                    .join(Alert, JoinType.INNER, AlertRecipient.alertId, Alert.id)
                    .update({
                        (Alert.id eq req.id) and
                            AlertRecipient.readAt.run { if (req.read) isNull() else isNotNull() } and
                            (AlertRecipient.recipientId eq user.userId)
                    }) {
                        if (req.read) {
                            it[AlertRecipient.readAt] = NowExpression(AlertRecipient.readAt)
                        } else {
                            it[AlertRecipient.readAt] = null
                        }
                    }

                if (result > 0) {
                    getStats(user.userId)
                } else { null }
            }

            respondStats(user, stats)
        }
    }

    post<AlertsApi.MarkAll> {
        val req = call.receive<AlertUpdateAll>()

        requireAuthorization(OauthScope.MARK_ALERTS) { _, user ->
            val stats = transaction {
                val result = AlertRecipient.update({
                    AlertRecipient.readAt.run { if (req.read) isNull() else isNotNull() } and
                        (AlertRecipient.recipientId eq user.userId)
                }) {
                    if (req.read) {
                        it[readAt] = NowExpression(readAt)
                    } else {
                        it[readAt] = null
                    }
                }

                if (result > 0) {
                    getStats(user.userId)
                } else {
                    null
                }
            }

            respondStats(user, stats)
        }
    }
}
