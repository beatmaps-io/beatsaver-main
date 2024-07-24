package io.beatmaps.login

import io.beatmaps.api.UserCrypto
import io.beatmaps.api.alertCount
import io.beatmaps.common.Config
import io.beatmaps.common.Folders
import io.beatmaps.common.db.upsert
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.UserDao
import io.beatmaps.util.requireAuthorization
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpMethod
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.OAuthAccessTokenResponse
import io.ktor.server.auth.OAuthServerSettings
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.locations.Location
import io.ktor.server.locations.get
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.util.StringValuesBuilder
import io.ktor.util.hex
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.io.File
import java.util.Base64

val discordSecret = System.getenv("DISCORD_HASH_SECRET")?.let { Base64.getDecoder().decode(it) } ?: byteArrayOf()

@Location("/discord")
class DiscordLogin(val state: String? = null)

@Serializable
data class DiscordUserInfo(
    val username: String,
    val id: Long,
    val avatar: String?
)

class DiscordHelper(val client: HttpClient) {
    fun discordProvider(state: String?) = OAuthServerSettings.OAuth2ServerSettings(
        name = "discord",
        authorizeUrl = "https://discord.com/api/oauth2/authorize",
        accessTokenUrl = "https://discord.com/api/oauth2/token",
        clientId = System.getenv("DISCORD_CLIENTID") ?: "",
        clientSecret = System.getenv("DISCORD_CLIENTSECRET") ?: "",
        requestMethod = HttpMethod.Post,
        defaultScopes = listOf("identify"),
        authorizeUrlInterceptor = {
            state?.let {
                val params = parameters as StringValuesBuilder
                params["state"] = it
            }
        }
    )

    suspend fun getDiscordAvatar(discordAvatar: String, discordId: Long) =
        client.get("https://cdn.discordapp.com/avatars/$discordId/$discordAvatar.png") {
            timeout {
                socketTimeoutMillis = 30000
                requestTimeoutMillis = 60000
            }
        }.body<ByteArray>()

    suspend fun downloadDiscordAvatar(discordAvatar: String, discordId: Long): String {
        val bytes = getDiscordAvatar(discordAvatar, discordId)
        val fileName = UserCrypto.getHash(discordId.toString(), discordSecret)
        val localFile = File(Folders.localAvatarFolder(), "$fileName.png")
        localFile.writeBytes(bytes)

        return "${Config.cdnBase("", true)}/avatar/$fileName.png"
    }

    suspend fun getDiscordData(token: String) =
        client.get("https://discord.com/api/users/@me") {
            header("Authorization", "Bearer $token")
        }.body<DiscordUserInfo>()
}

fun Route.discordLogin(client: HttpClient) {
    val discordHelper = DiscordHelper(client)

    suspend fun ApplicationCall.getDiscordData(): DiscordUserInfo {
        val principal = authentication.principal<OAuthAccessTokenResponse.OAuth2>() ?: error("No principal")
        return discordHelper.getDiscordData(principal.accessToken)
    }

    authenticate("discord") {
        get<DiscordLogin> { req ->
            val data = call.getDiscordData()

            val avatarLocal = data.avatar?.let { discordHelper.downloadDiscordAvatar(it, data.id) }

            val (user, alertCount) = transaction {
                val userId = User.upsert(User.discordId) {
                    it[name] = data.username
                    it[discordId] = data.id
                    it[avatar] = avatarLocal
                    it[active] = true
                }.value

                UserDao[userId] to alertCount(userId)
            }

            call.sessions.set(Session.fromUser(user, alertCount, call = call))
            req.state?.let { String(hex(it)) }.orEmpty().let { query ->
                if (query.isNotEmpty() && query.contains("client_id")) {
                    call.respondRedirect("/oauth2/authorize/success$query")
                } else if (query.isNotEmpty() && query.contains("code")) {
                    call.respondRedirect("/quest$query")
                } else {
                    call.respondRedirect("/")
                }
            }
        }

        get("/discord-link") {
            requireAuthorization { _, sess ->
                val data = call.getDiscordData()

                newSuspendedTransaction {
                    val (existingMaps, dualAccount) = User
                        .join(Beatmap, JoinType.LEFT, User.id, Beatmap.uploader) {
                            Beatmap.deletedAt.isNull()
                        }
                        .select(User.discordId, User.email, Beatmap.id.count())
                        .where {
                            (User.discordId eq data.id) and User.active
                        }
                        .groupBy(User.id)
                        .firstOrNull()?.let {
                            it[Beatmap.id.count()] to (it[User.email] != null)
                        } ?: (0L to null)

                    if (existingMaps > 0 || dualAccount == true) {
                        // User has maps, can't link
                        return@newSuspendedTransaction
                    } else if (dualAccount == false) {
                        // Email = false means the other account is a pure discord account
                        // and as it has no maps we can set it to inactive before linking it to the current account
                        User.update({ User.discordId eq data.id }) {
                            it[active] = false
                            it[discordId] = null
                        }
                    }

                    val avatarLocal = data.avatar?.let { discordHelper.downloadDiscordAvatar(it, data.id) }

                    User.update({ User.id eq sess.userId }) {
                        it[discordId] = data.id
                        it[avatar] = avatarLocal
                    }
                }

                call.respondRedirect("/profile#account")
            }
        }
    }
}
