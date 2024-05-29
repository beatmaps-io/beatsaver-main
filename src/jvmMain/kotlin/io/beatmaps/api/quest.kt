package io.beatmaps.api

import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.UserDao
import io.beatmaps.login.server.DBClientService
import io.beatmaps.login.server.toIdentity
import io.beatmaps.util.requireAuthorization
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.locations.Location
import io.ktor.server.locations.post
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import nl.myndocs.oauth2.tokenstore.inmemory.InMemoryDeviceCodeStore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

@Location("/api/quest")
class QuestApi {
    @Location("/code")
    data class Code(val api: QuestApi)

    @Location("/complete")
    data class Complete(val api: QuestApi)
}

fun Route.questRoute(deviceCodeStore: InMemoryDeviceCodeStore) {
    post<QuestApi.Code> {
        val req = call.receive<QuestCode>()
        deviceCodeStore.getForUserCode(req.code)?.let { code ->
            if (code.complete) {
                null
            } else {
                val client = DBClientService.getClient(code.clientId)
                call.respond(QuestCodeResponse(code.deviceCode, client?.name, client?.iconUrl, code.scopes))
            }
        } ?: call.respond(HttpStatusCode.BadRequest)
    }

    post<QuestApi.Complete> {
        val req = call.receive<QuestComplete>()

        requireAuthorization { _, sess ->
            newSuspendedTransaction {
                User.selectAll().where {
                    (User.id eq sess.userId)
                }.firstOrNull()?.let { UserDao.wrapRow(it) }
            }?.let { user ->
                deviceCodeStore.getForDeviceCode(req.deviceCode)?.let { code ->
                    deviceCodeStore.storeDeviceCode(
                        code.copy(identity = user.toIdentity(), complete = true)
                    )
                    call.respond(HttpStatusCode.OK)
                } ?: call.respond(HttpStatusCode.BadRequest)
            }
        }
    }
}
