package io.beatmaps.login

import com.fasterxml.jackson.module.kotlin.readValue
import io.beatmaps.api.alertCount
import io.beatmaps.api.requireAuthorization
import io.beatmaps.common.Config
import io.beatmaps.common.client
import io.beatmaps.common.db.upsert
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.UserDao
import io.beatmaps.common.jackson
import io.beatmaps.common.localAvatarFolder
import io.beatmaps.genericPage
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.auth.OAuthAccessTokenResponse
import io.ktor.auth.authenticate
import io.ktor.auth.authentication
import io.ktor.auth.principal
import io.ktor.client.features.timeout
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.features.NotFoundException
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.parametersOf
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.locations.post
import io.ktor.response.respondRedirect
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.sessions.clear
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import io.ktor.sessions.set
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.io.File
import java.lang.Long.parseLong

data class Session(
    val userId: Int,
    val hash: String? = null,
    val userEmail: String?,
    val userName: String,
    val testplay: Boolean = false,
    val steamId: Long? = null,
    val oculusId: Long? = null,
    val admin: Boolean = false,
    val uniqueName: String? = null,
    val canLink: Boolean = false,
    val alerts: Int? = null
) {
    constructor(userId: Int, hash: String?, userEmail: String, userName: String, testplay: Boolean, steamId: Long?, oculusId: Long?, admin: Boolean, uniqueName: String?, canLink: Boolean) :
        this(userId, hash, userEmail, userName, testplay, steamId, oculusId, admin, uniqueName, canLink, transaction { alertCount(userId) })
    constructor(userId: Int, hash: String?, userEmail: String, userName: String, testplay: Boolean, steamId: Long?, oculusId: Long?, admin: Boolean, uniqueName: String?) :
        this(userId, hash, userEmail, userName, testplay, steamId, oculusId, admin, uniqueName, true)
    constructor(userId: Int, hash: String?, userEmail: String, userName: String, testplay: Boolean, steamId: Long?, oculusId: Long?, admin: Boolean) :
        this(userId, hash, userEmail, userName, testplay, steamId, oculusId, admin, null)
    constructor(userId: Int, hash: String?, userEmail: String, userName: String, testplay: Boolean, steamId: Long?, oculusId: Long?) :
        this(userId, hash, userEmail, userName, testplay, steamId, oculusId, false)
    constructor(userId: Int, userEmail: String, userName: String, testplay: Boolean, steamId: Long?, oculusId: Long?) :
        this(userId, null, userEmail, userName, testplay, steamId, oculusId)

    fun isAdmin() = admin && transaction { UserDao[userId].admin }
}

@Location("/login") class Login
@Location("/register") class Register
@Location("/forgot") class Forgot
@Location("/reset/{jwt}") class Reset(val jwt: String)
@Location("/verify") class Verify
@Location("/username") class Username

fun Route.authRoute() {
    suspend fun downloadDiscordAvatar(discordAvatar: String, discordId: Long): String {
        val bytes = client.get<ByteArray>("https://cdn.discordapp.com/avatars/$discordId/$discordAvatar.png") {
            timeout {
                socketTimeoutMillis = 30000
                requestTimeoutMillis = 60000
            }
        }
        val localFile = File(localAvatarFolder(), "$discordId.png")
        localFile.writeBytes(bytes)

        return "${Config.cdnbase}/avatar/$discordId.png"
    }

    suspend fun ApplicationCall.getDiscordData(): Map<String, Any?> {
        val principal = authentication.principal<OAuthAccessTokenResponse.OAuth2>() ?: error("No principal")

        val json = client.get<String>("https://discord.com/api/users/@me") {
            header("Authorization", "Bearer ${principal.accessToken}")
        }

        return jackson.readValue(json)
    }

    authenticate("discord") {
        get("/discord") {
            val data = call.getDiscordData()

            val discordName = data["username"] as String

            val discordIdLocal = parseLong(data["id"] as String)
            val avatarLocal = (data["avatar"] as String?)?.let { downloadDiscordAvatar(it, discordIdLocal) }

            val user = transaction {
                val userId = User.upsert(User.discordId) {
                    it[name] = discordName
                    it[discordId] = discordIdLocal
                    it[avatar] = avatarLocal
                    it[active] = true
                }.value

                UserDao[userId]
            }

            call.sessions.set(Session(user.id.value, user.hash, "", discordName, user.testplay, user.steamId, user.oculusId, user.admin, user.uniqueName, user.hash == null, alertCount(user.id.value)))
            call.respondRedirect("/")
        }
    }

    authenticate("discord") {
        get("/discord-link") {
            requireAuthorization { sess ->
                val data = call.getDiscordData()

                val discordIdLocal = parseLong(data["id"] as String)

                newSuspendedTransaction {
                    val (existingMaps, dualAccount) = User
                        .join(Beatmap, JoinType.LEFT, User.id, Beatmap.uploader) {
                            Beatmap.deletedAt.isNull()
                        }
                        .slice(User.discordId, User.email, Beatmap.id.count())
                        .select {
                            (User.discordId eq discordIdLocal) and User.active
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
                        User.update({ User.discordId eq discordIdLocal }) {
                            it[active] = false
                            it[discordId] = null
                        }
                    }

                    val avatarLocal = (data["avatar"] as String?)?.let { downloadDiscordAvatar(it, discordIdLocal) }

                    User.update({ User.id eq sess.userId }) {
                        it[discordId] = discordIdLocal
                        it[avatar] = avatarLocal
                    }
                }

                call.respondRedirect("/profile#account")
            }
        }
    }

    get<Register> { genericPage() }
    get<Forgot> { genericPage() }
    get<Reset> { genericPage() }

    get<Verify> {
        val query = call.request.queryParameters
        val userId = (query["user"] ?: throw NotFoundException("User not specified")).toInt()
        val token = query["token"] ?: ""

        val valid = transaction {
            User.update({
                (User.id eq userId) and (User.verifyToken eq token)
            }) {
                it[active] = true
                it[verifyToken] = null
            } > 0
        }

        call.respondRedirect("/login" + if (valid) "?valid" else "")
    }

    authenticate("auth-form") {
        post<Login> {
            call.principal<SimpleUserPrincipal>()?.let { newPrincipal ->
                val user = newPrincipal.user
                call.sessions.set(Session(user.id.value, user.hash, user.email, user.name, user.testplay, user.steamId, user.oculusId, user.admin, user.uniqueName, false, alertCount(user.id.value)))
            }
            call.respondRedirect("/")
        }
    }

    get<Login> {
        call.sessions.get<Session>()?.let {
            call.respondRedirect("/profile")
        } ?: run {
            genericPage()
        }
    }

    get<Username> {
        genericPage()
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
            val params = parametersOf(
                "openid.ns" to listOf("http://specs.openid.net/auth/2.0"),
                "openid.mode" to listOf("checkid_setup"),
                "openid.return_to" to listOf("${Config.basename}/steam"),
                "openid.realm" to listOf(Config.basename),
                "openid.identity" to listOf("http://specs.openid.net/auth/2.0/identifier_select"),
                "openid.claimed_id" to listOf("http://specs.openid.net/auth/2.0/identifier_select")
            )

            // val url = URLBuilder(protocol = URLProtocol.HTTPS, host = "steamcommunity.com", encodedPath = "/openid/login", parameters = params).buildString()
            val url = Url(URLProtocol.HTTPS, "steamcommunity.com", 0, "/openid/login", params, "", null, null, false).toString()
            call.respondRedirect(url)
        } else {
            val xml = client.submitForm<String>(
                "https://steamcommunity.com/openid/login",
                formParameters = parametersOf(
                    "openid.ns" to listOf("http://specs.openid.net/auth/2.0"),
                    "openid.mode" to listOf("check_authentication"),
                    "openid.sig" to listOf(call.request.queryParameters["openid.sig"] ?: ""),
                    *(call.request.queryParameters["openid.signed"])?.split(",")?.map {
                        "openid.$it" to listOf(call.request.queryParameters["openid.$it"] ?: "")
                    }?.toTypedArray() ?: arrayOf()
                )
            )
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
            call.sessions.set(sess.copy(steamId = steamid))
            call.respondRedirect("/profile")
        }
    }
}
