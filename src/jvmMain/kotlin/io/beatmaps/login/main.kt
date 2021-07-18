package io.beatmaps.login

import com.fasterxml.jackson.module.kotlin.readValue
import io.beatmaps.common.Config
import io.beatmaps.common.client
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.UserDao
import io.beatmaps.common.db.upsert
import io.beatmaps.common.jackson
import io.ktor.application.call
import io.ktor.auth.OAuthAccessTokenResponse
import io.ktor.auth.authenticate
import io.ktor.auth.authentication
import io.ktor.client.features.timeout
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.Parameters
import io.ktor.http.ParametersBuilder
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.response.respondRedirect
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.sessions.clear
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import io.ktor.sessions.set
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.io.File
import java.lang.Long.parseLong

fun localAvatarFolder() = File(System.getenv("AVATAR_DIR") ?: "K:\\BMAvatar")

data class Session(val userId: Int, val hash: String? = null, val userEmail: String, val userName: String, val testplay: Boolean = false,
                   val steamId: Long? = null, val oculusId: Long? = null, val admin: Boolean = false) {
    constructor(userId: Int, hash: String?, userEmail: String, userName: String, testplay: Boolean, steamId: Long?, oculusId: Long?) :
            this(userId, hash, userEmail, userName, testplay, steamId, oculusId, false)
    constructor(userId: Int, userEmail: String, userName: String, testplay: Boolean, steamId: Long?, oculusId: Long?) :
            this(userId, null, userEmail, userName, testplay, steamId, oculusId)
}

fun Route.authRoute() {
    authenticate("discord") {
        get ("/login") {
            val principal = call.authentication.principal<OAuthAccessTokenResponse.OAuth2>() ?: error("No principal")

            val json = client.get<String>("https://discord.com/api/users/@me") {
                header("Authorization", "Bearer ${principal.accessToken}")
            }

            val data = jackson.readValue<Map<String, Any?>>(json)

            val discordEmail = data["email"] as String
            val discordName = data["username"] as String
            val discordAvatar = data["avatar"] as String?
            val discordIdLocal = parseLong(data["id"] as String)
            val avatarLocal = discordAvatar?.let { a ->
                val bytes = client.get<ByteArray>("https://cdn.discordapp.com/avatars/$discordIdLocal/$a.png") {
                    timeout {
                        socketTimeoutMillis = 30000
                        requestTimeoutMillis = 60000
                    }
                }
                val localFile = File(localAvatarFolder(), "${discordIdLocal}.png")
                localFile.writeBytes(bytes)

                "${Config.cdnbase}/avatar/${discordIdLocal}.png"
            }

            val user = transaction {
                val userId = User.upsert(User.discordId) {
                    it[name] = discordName
                    it[email] = discordEmail
                    it[discordId] = discordIdLocal
                    it[avatar] = avatarLocal
                }.value

                UserDao[userId]
            }

            call.sessions.set(Session(user.id.value, user.hash, discordEmail, discordName, user.testplay, user.steamId, user.oculusId, user.admin))
            call.respondRedirect("/")
        }
    }

    get("/logout") {
        call.sessions.clear<Session>()
        call.respondRedirect("/")
    }

    get("/steam") {
        val sess = call.sessions.get<Session>()
        if (sess == null) {
            call.respondRedirect("/")
            return@get
        }

        val claimedId = call.request.queryParameters["openid.claimed_id"]

        if (claimedId == null) {
            val params = ParametersBuilder().apply {
                append("openid.ns", "http://specs.openid.net/auth/2.0")
                append("openid.mode", "checkid_setup")
                append("openid.return_to", "${Config.basename}/steam")
                append("openid.realm", Config.basename)
                append("openid.identity", "http://specs.openid.net/auth/2.0/identifier_select")
                append("openid.claimed_id", "http://specs.openid.net/auth/2.0/identifier_select")
            }

            val url = URLBuilder(protocol = URLProtocol.HTTPS, host = "steamcommunity.com", encodedPath = "/openid/login", parameters = params).buildString()
            call.respondRedirect(url)
        } else {
            val xml = client.submitForm<String>("https://steamcommunity.com/openid/login", formParameters = Parameters.build {
                append("openid.ns", "http://specs.openid.net/auth/2.0")
                append("openid.mode", "check_authentication")
                append("openid.sig", call.request.queryParameters["openid.sig"] ?: "")
                (call.request.queryParameters["openid.signed"])?.split(",")?.forEach {
                    append("openid.$it", call.request.queryParameters["openid.$it"] ?: "")
                }
            })
            val valid = Regex("is_valid\\s*:\\s*true", RegexOption.IGNORE_CASE).containsMatchIn(xml)
            if (!valid) {
                throw RuntimeException("Invalid openid response 1")
            }

            val matches = Regex("^https?:\\/\\/steamcommunity\\.com\\/openid\\/id\\/(7[0-9]{15,25}+)\$").matchEntire(claimedId)?.groupValues
                ?: throw RuntimeException("Invalid openid response 2")
            val steamid = matches[1].toLong()

            transaction {
                User.update({ User.id eq sess.userId }) {
                    it[steamId] = steamid
                }
            }
            call.respondRedirect("/profile")
        }
    }
}