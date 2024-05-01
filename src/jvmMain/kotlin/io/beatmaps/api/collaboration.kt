package io.beatmaps.api

import io.beatmaps.common.api.EAlertType
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.db.updateReturning
import io.beatmaps.common.dbo.Alert
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.BeatmapDao
import io.beatmaps.common.dbo.Collaboration
import io.beatmaps.common.dbo.CollaborationDao
import io.beatmaps.common.dbo.Follows
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.UserDao
import io.beatmaps.common.dbo.collaboratorAlias
import io.beatmaps.util.cdnPrefix
import io.beatmaps.util.isUploader
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
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

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
        requireAuthorization { sess ->
            val req = call.receive<CollaborationRequestData>()

            val success = transaction {
                (isUploader(req.mapId, sess.userId) && !sess.suspended).also { authorized ->
                    if (authorized) {
                        Collaboration.insertAndGetId {
                            it[mapId] = req.mapId
                            it[collaboratorId] = req.collaboratorId
                            it[requestedAt] = NowExpression(requestedAt.columnType)
                            it[uploadedAt] = Beatmap.slice(Beatmap.uploaded).select { Beatmap.id eq req.mapId }
                        }
                    }
                }
            }

            call.respond(if (success) HttpStatusCode.OK else HttpStatusCode.Unauthorized)
        }
    }

    post<CollaborationApi.CollaborationResponse> {
        requireAuthorization { sess ->
            val req = call.receive<CollaborationResponseData>()

            val success = transaction {
                if (req.accepted) {
                    val mapId = Collaboration.updateReturning(
                        {
                            Collaboration.id eq req.collaborationId and (Collaboration.collaboratorId eq sess.userId)
                        },
                        {
                            it[accepted] = true
                        },
                        Collaboration.mapId
                    ).let {
                        it?.firstOrNull()
                    }

                    if (mapId != null) {
                        // Generate alert for followers of the collaborator.
                        val map = mapId.let {
                            BeatmapDao.wrapRow(Beatmap.select {
                                Beatmap.id eq it[Collaboration.mapId].value
                            }.single())
                        }

                        val followsAlias = Follows.alias("f2")
                        val recipients = Follows
                            .join(followsAlias, JoinType.LEFT, followsAlias[Follows.followerId], Follows.followerId) {
                                (followsAlias[Follows.userId] eq map.uploaderId) and followsAlias[Follows.following]
                            }.select {
                                followsAlias[Follows.id].isNull() and (Follows.followerId neq map.uploaderId) and
                                        (Follows.userId eq sess.userId) and Follows.upload and Follows.following
                            }.map { row ->
                                row[Follows.followerId].value
                            } - listOf(map.uploaderId.value).toSet()

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
                        true
                    } else {
                        false
                    }
                } else {
                    Collaboration.deleteWhere {
                        Collaboration.id eq req.collaborationId and (Collaboration.collaboratorId eq sess.userId)
                    }
                    true
                }
            }

            call.respond(if (success) HttpStatusCode.OK else HttpStatusCode.Unauthorized)
        }
    }

    post<CollaborationApi.CollaborationRemove> {
        requireAuthorization { sess ->
            val req = call.receive<CollaborationRemoveData>()

            val success = transaction {
                (isUploader(req.mapId, sess.userId) || sess.isAdmin()) &&
                    Collaboration.deleteWhere {
                        Collaboration.mapId eq req.mapId and (Collaboration.collaboratorId eq req.collaboratorId)
                    } > 0
            }

            call.respond(if (success) HttpStatusCode.OK else HttpStatusCode.Unauthorized)
        }
    }

    get<CollaborationApi.Collaborations> {
        requireAuthorization { sess ->
            val mapId = it.id.toInt(16)

            val collaborations = transaction {
                if (isUploader(mapId, sess.userId) || sess.admin) {
                    Collaboration
                        .join(collaboratorAlias, JoinType.LEFT, Collaboration.collaboratorId, collaboratorAlias[User.id])
                        .select {
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
