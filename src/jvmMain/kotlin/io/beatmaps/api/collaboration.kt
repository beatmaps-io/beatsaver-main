package io.beatmaps.api

import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Collaboration
import io.beatmaps.common.dbo.CollaborationDao
import io.beatmaps.common.dbo.User
import io.beatmaps.util.isUploader
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.locations.Location
import io.ktor.server.locations.get
import io.ktor.server.locations.post
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

fun CollaborationDetail.Companion.from(row: ResultRow) = CollaborationDao.wrapRow(row).let {
    CollaborationDetail(it.mapId.value, UserDetail.from(row), it.accepted)
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
                    Collaboration.update({
                        Collaboration.id eq req.collaborationId and (Collaboration.collaboratorId eq sess.userId)
                    }) {
                        it[accepted] = true
                    } > 0
                } else {
                    Collaboration.deleteWhere {
                        Collaboration.id eq req.collaborationId and (Collaboration.collaboratorId eq sess.userId)
                    } > 0
                }
            }

            call.respond(if (success) HttpStatusCode.OK else HttpStatusCode.Unauthorized)
        }
    }

    post<CollaborationApi.CollaborationRemove> {
        requireAuthorization { sess ->
            val req = call.receive<CollaborationRemoveData>()

            val success = transaction {
                isUploader(req.mapId, sess.userId) &&
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
                        .join(User, JoinType.LEFT, Collaboration.collaboratorId, User.id)
                        .select {
                            Collaboration.mapId eq mapId
                        }
                        .orderBy(Collaboration.accepted, SortOrder.DESC)
                        .map { row ->
                            CollaborationDetail.from(row)
                        }
                } else {
                    null
                }
            }
            call.respond(collaborations ?: HttpStatusCode.Unauthorized)
        }
    }
}
