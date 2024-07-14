package io.beatmaps.api

import io.beatmaps.common.api.EAlertType
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.dbo.Alert
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.BeatmapDao
import io.beatmaps.common.dbo.Collaboration
import io.beatmaps.common.dbo.CollaborationDao
import io.beatmaps.common.dbo.Follows
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.UserDao
import io.beatmaps.common.dbo.collaboratorAlias
import io.beatmaps.common.dbo.joinUploader
import io.beatmaps.util.cdnPrefix
import io.beatmaps.util.isUploader
import io.beatmaps.util.requireAuthorization
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.locations.Location
import io.ktor.server.locations.get
import io.ktor.server.locations.post
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.vendors.ForUpdateOption

fun CollaborationDetail.Companion.from(row: ResultRow, cdnPrefix: String) = CollaborationDao.wrapRow(row).let {
    CollaborationDetail(
        it.id.value,
        it.mapId.value,
        if (row.hasValue(collaboratorAlias[User.id])) UserDetail.from(UserDao.wrapRow(row, collaboratorAlias)) else null,
        if (row.hasValue(Beatmap.id)) MapDetail.from(row, cdnPrefix) else null,
        it.requestedAt.toKotlinInstant(),
        it.accepted
    )
}

@Location("/api/collaborations")
class CollaborationApi {
    @Location("/request")
    data class CollaborationRequest(val api: CollaborationApi)

    @Location("/response")
    data class CollaborationResponse(val api: CollaborationApi)

    @Location("/remove")
    data class CollaborationRemove(val api: CollaborationApi)

    @Location("/map/{id}")
    data class Collaborations(val api: CollaborationApi, val id: String)
}

fun Route.collaborationRoute() {
    post<CollaborationApi.CollaborationRequest> {
        requireAuthorization { _, sess ->
            val req = call.receive<CollaborationRequestData>()

            val success = transaction {
                (isUploader(req.mapId, sess.userId) && !sess.suspended).also { authorized ->
                    if (authorized) {
                        Collaboration.insertAndGetId {
                            it[mapId] = req.mapId
                            it[collaboratorId] = req.collaboratorId
                            it[requestedAt] = NowExpression(requestedAt)
                            it[uploadedAt] = Beatmap.select(Beatmap.uploaded).where { Beatmap.id eq req.mapId }
                        }
                    }
                }
            }

            call.respond(if (success) HttpStatusCode.OK else HttpStatusCode.Unauthorized)
        }
    }

    post<CollaborationApi.CollaborationResponse> {
        requireAuthorization { _, sess ->
            val req = call.receive<CollaborationResponseData>()

            val success = transaction {
                if (req.accepted) {
                    val (collab, map) = Collaboration
                        .join(Beatmap, JoinType.LEFT, Collaboration.mapId, Beatmap.id) { Beatmap.deletedAt.isNull() }
                        .joinUploader()
                        .selectAll()
                        .where {
                            Collaboration.id eq req.collaborationId and (Collaboration.collaboratorId eq sess.userId)
                        }
                        .forUpdate(ForUpdateOption.PostgreSQL.ForUpdate(null, Collaboration))
                        .singleOrNull()
                        ?.let {
                            UserDao.wrapRow(it)
                            CollaborationDao.wrapRow(it) to BeatmapDao.wrapRow(it)
                        } ?: (null to null)

                    if (collab?.accepted == true) {
                        true
                    } else if (map != null && collab != null) {
                        // Set to accepted
                        Collaboration.update({
                            Collaboration.id eq collab.id
                        }) {
                            it[accepted] = true
                        }

                        // Generate alert for followers of the collaborator, if the map has already been published.
                        if (map.lastPublishedAt != null) {
                            val followsAlias = Follows.alias("f2")
                            val recipients = Follows
                                .join(
                                    followsAlias,
                                    JoinType.LEFT,
                                    followsAlias[Follows.followerId],
                                    Follows.followerId
                                ) {
                                    (followsAlias[Follows.userId] eq map.uploaderId) and followsAlias[Follows.following]
                                }
                                .select(Follows.followerId)
                                .where {
                                    followsAlias[Follows.id].isNull() and (Follows.followerId neq map.uploaderId) and
                                            (Follows.userId eq sess.userId) and Follows.collab and Follows.following
                                }
                                .map { row ->
                                    row[Follows.followerId].value
                                }

                            Alert.insert(
                                "New Map Collaboration",
                                "@${sess.uniqueName} collaborated with @${map.uploader.uniqueName} on #${
                                    Integer.toHexString(
                                        map.id.value
                                    )
                                }: **${map.name}**.\n" +
                                        "*\"${map.description.replace(Regex("\n+"), " ").take(100)}...\"*",
                                EAlertType.MapRelease,
                                recipients
                            )
                        }
                        true
                    } else {
                        false
                    }
                } else {
                    Collaboration.deleteWhere {
                        id eq req.collaborationId and (collaboratorId eq sess.userId)
                    } > 0
                }
            }

            call.respond(if (success) HttpStatusCode.OK else HttpStatusCode.Unauthorized)
        }
    }

    post<CollaborationApi.CollaborationRemove> {
        requireAuthorization { _, sess ->
            val req = call.receive<CollaborationRemoveData>()

            val success = transaction {
                (isUploader(req.mapId, sess.userId) || sess.isAdmin()) &&
                    Collaboration.deleteWhere {
                        mapId eq req.mapId and (collaboratorId eq req.collaboratorId)
                    } > 0
            }

            call.respond(if (success) HttpStatusCode.OK else HttpStatusCode.Unauthorized)
        }
    }

    get<CollaborationApi.Collaborations> {
        requireAuthorization { _, sess ->
            val mapId = it.id.toInt(16)

            val collaborations = transaction {
                if (isUploader(mapId, sess.userId) || sess.admin) {
                    Collaboration
                        .join(collaboratorAlias, JoinType.LEFT, Collaboration.collaboratorId, collaboratorAlias[User.id])
                        .selectAll()
                        .where {
                            Collaboration.mapId eq mapId
                        }
                        .orderBy(Collaboration.accepted, SortOrder.DESC)
                        .map { row ->
                            CollaborationDetail.from(row, cdnPrefix())
                        }
                } else {
                    null
                }
            }
            call.respond(collaborations ?: HttpStatusCode.Unauthorized)
        }
    }
}
