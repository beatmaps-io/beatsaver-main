package io.beatmaps.login.server

import io.beatmaps.api.OauthScope
import io.beatmaps.common.Config
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.UserDao
import io.beatmaps.genericPage
import io.beatmaps.login.Session
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.request.httpMethod
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlinx.coroutines.runBlocking
import kotlinx.html.meta
import nl.myndocs.oauth2.authenticator.Credentials
import nl.myndocs.oauth2.client.Client
import nl.myndocs.oauth2.device.DeviceCodeUrlGenerator
import nl.myndocs.oauth2.device.UUIDDeviceCodeConverter
import nl.myndocs.oauth2.identity.Identity
import nl.myndocs.oauth2.identity.IdentityService
import nl.myndocs.oauth2.ktor.feature.Oauth2ServerFeature
import nl.myndocs.oauth2.token.RefreshToken
import nl.myndocs.oauth2.token.converter.RefreshTokenConverter
import nl.myndocs.oauth2.tokenstore.inmemory.InMemoryDeviceCodeStore
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

fun Application.installOauth2(deviceCodeStore: InMemoryDeviceCodeStore) {
    install(Oauth2ServerFeature) {
        authenticationCallback = { call, callRouter ->
            if (call.request.httpMethod == HttpMethod.Get) {
                val userSession = call.sessions.get<Session>()
                val reqClientId = call.parameters["client_id"]

                if (reqClientId != null && userSession?.oauth2ClientId == reqClientId) {
                    callRouter.route(BSCallContext(call), Credentials(userSession.userId.toString(), ""))
                } else {
                    runBlocking {
                        call.genericPage(headerTemplate = {
                            reqClientId?.let { DBClientService.getClient(it) }?.let { client ->
                                meta("oauth-data", "{\"id\": \"${client.clientId}\", \"name\": \"${client.name}\", \"icon\": \"${client.iconUrl}\"}")
                            }
                        })
                    }
                }
            }
        }

        tokenInfoCallback = {
            val user = it.identity?.metadata?.get("object") as UserDao

            val out = mapOf(
                "scopes" to it.scopes
            )

            if (it.scopes.contains(OauthScope.IDENTITY.tag)) {
                out.plus(
                    mapOf(
                        "id" to it.identity?.username,
                        "name" to user.uniqueName,
                        "avatar" to user.avatar
                    )
                )
            } else {
                out
            }
        }

        tokenEndpoint = "/api/oauth2/token"
        authorizationEndpoint = "/oauth2/authorize"
        tokenInfoEndpoint = "/api/oauth2/identity"
        deviceCodeEndpoint = "/api/oauth2/devicecode"

        identityService = object : IdentityService {
            override fun allowedScopes(forClient: Client, identity: Identity, scopes: Set<String>) = scopes

            override fun identityOf(forClient: Client, username: String) = Identity(username)

            override fun validCredentials(forClient: Client, identity: Identity, password: String) =
                transaction {
                    !User.selectAll().where { (User.id eq identity.username.toInt()) and User.active }.empty()
                }
        }

        clientService = DBClientService
        tokenStore = DBTokenStore

        // Refresh tokens will last 45 days, will refresh after 15 days (30 left)
        refreshTokenConverter = object : RefreshTokenConverter {
            val validTime = 45 * 86400L
            val refreshAfter = 30 * 86400L

            override fun convertToToken(refreshToken: RefreshToken) =
                if (refreshToken.expiresIn() < refreshAfter) {
                    convertToToken(refreshToken.identity, refreshToken.clientId, refreshToken.scopes)
                } else {
                    refreshToken
                }

            override fun convertToToken(identity: Identity?, clientId: String, requestedScopes: Set<String>): RefreshToken {
                return RefreshToken(
                    UUID.randomUUID().toString(),
                    Instant.now().plusSeconds(validTime),
                    identity,
                    clientId,
                    requestedScopes
                )
            }
        }

        this.deviceCodeStore = deviceCodeStore
        deviceCodeConverter = UUIDDeviceCodeConverter(
            deviceCodeUrlGenerator = object : DeviceCodeUrlGenerator {
                override fun getUrl() = Config.siteBase() + "/quest"
                override fun getUrl(code: String) = getUrl() + "?code=$code"
            }
        )
    }
}
