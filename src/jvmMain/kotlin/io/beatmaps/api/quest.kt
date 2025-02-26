package io.beatmaps.api

import de.nielsfalk.ktor.swagger.Ignore
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.UserDao
import io.beatmaps.login.server.DBClientService
import io.beatmaps.login.server.toIdentity
import io.beatmaps.util.requireAuthorization
import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.request.receive
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import nl.myndocs.oauth2.tokenstore.inmemory.InMemoryDeviceCodeStore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

@Resource("/api/quest")
class QuestApi {
    @Resource("/code")
    data class Code(
        @Ignore
        val api: QuestApi
    )

    @Resource("/complete")
    data class Complete(
        @Ignore
        val api: QuestApi
    )
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
