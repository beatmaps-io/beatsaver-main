package io.beatmaps.login

import io.beatmaps.api.UserCrypto
import io.beatmaps.api.alertCount
import io.beatmaps.api.parseJwtUntrusted
import io.beatmaps.api.requireAuthorization
import io.beatmaps.common.Config
import io.beatmaps.common.client
import io.beatmaps.common.db.upsert
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.UserDao
import io.beatmaps.common.getCountry
import io.beatmaps.common.localAvatarFolder
import io.beatmaps.genericPage
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.SignatureException
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.parametersOf
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.OAuthAccessTokenResponse
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.auth.principal
import io.ktor.server.locations.Location
import io.ktor.server.locations.get
import io.ktor.server.locations.post
import io.ktor.server.plugins.origin
import io.ktor.server.request.httpMethod
import io.ktor.server.request.queryString
import io.ktor.server.request.userAgent
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.util.StringValues
import io.ktor.util.hex
import kotlinx.coroutines.runBlocking
import kotlinx.html.meta
import kotlinx.serialization.Serializable
import nl.myndocs.oauth2.authenticator.Credentials
import nl.myndocs.oauth2.client.Client
import nl.myndocs.oauth2.identity.Identity
import nl.myndocs.oauth2.identity.IdentityService
import nl.myndocs.oauth2.ktor.feature.Oauth2ServerFeature
import nl.myndocs.oauth2.token.RefreshToken
import nl.myndocs.oauth2.token.converter.RefreshTokenConverter
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.io.File
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Serializable
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
    val alerts: Int? = null,
    val curator: Boolean = false,
    val oauth2ClientId: String? = null,
    val suspended: Boolean = false,
    val ip: String? = null,
    val userAgent: String? = null,
    val countryCode: String? = null
) {
    fun isAdmin() = admin && transaction { UserDao[userId].admin }
    fun isCurator() = isAdmin() || (curator && transaction { UserDao[userId].curator })

    companion object {
        fun fromUser(user: UserDao, alertCount: Int? = null, oauth2ClientId: String? = null, call: ApplicationCall? = null) = Session(
            user.id.value, user.hash, user.email, user.name, user.testplay, user.steamId, user.oculusId, user.admin, user.uniqueName, false, alertCount,
            user.curator, oauth2ClientId, user.suspendedAt != null, call?.request?.origin?.remoteHost, call?.request?.userAgent(),
            call?.getCountry()?.let { if (it.success) it.countryCode else null }
        )
    }
}

@Location("/discord") class DiscordLogin(val state: String? = null)
@Location("/login") class Login
@Location("/oauth2") class Oauth2 {
    @Location("/authorize") class Authorize(val client_id: String, val api: Oauth2)
    @Location("/authorize/success") class AuthorizeSuccess(val client_id: String, val api: Oauth2)
    @Location("/authorize/not-me") class NotMe(val api: Oauth2)
}
@Location("/register") class Register
@Location("/forgot") class Forgot
@Location("/reset/{jwt}") class Reset(val jwt: String)
@Location("/verify/{jwt}") data class Verify(
    val jwt: String
)
@Location("/username") class Username
@Location("/steam") class Steam
data class DiscordUserInfo(
    val username: String,
    val id: Long,
    val avatar: String?
)

val discordSecret = System.getenv("DISCORD_HASH_SECRET")?.let { Base64.getDecoder().decode(it) } ?: byteArrayOf()

fun Route.authRoute() {
    suspend fun downloadDiscordAvatar(discordAvatar: String, discordId: Long): String {
        val bytes = client.get("https://cdn.discordapp.com/avatars/$discordId/$discordAvatar.png") {
            timeout {
                socketTimeoutMillis = 30000
                requestTimeoutMillis = 60000
            }
        }.body<ByteArray>()

        val fileName = UserCrypto.getHash(discordId.toString(), discordSecret)
        val localFile = File(localAvatarFolder(), "$fileName.png")
        localFile.writeBytes(bytes)

        return "${Config.cdnBase("", true)}/avatar/$fileName.png"
    }

    suspend fun ApplicationCall.getDiscordData(): DiscordUserInfo {
        val principal = authentication.principal<OAuthAccessTokenResponse.OAuth2>() ?: error("No principal")

        return client.get("https://discord.com/api/users/@me") {
            header("Authorization", "Bearer ${principal.accessToken}")
        }.body()
    }

    authenticate("discord") {
        get<DiscordLogin> { req ->
            val data = call.getDiscordData()

            val avatarLocal = data.avatar?.let { downloadDiscordAvatar(it, data.id) }

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
                } else {
                    call.respondRedirect("/")
                }
            }
        }
    }

    authenticate("discord") {
        get("/discord-link") {
            requireAuthorization { sess ->
                val data = call.getDiscordData()

                newSuspendedTransaction {
                    val (existingMaps, dualAccount) = User
                        .join(Beatmap, JoinType.LEFT, User.id, Beatmap.uploader) {
                            Beatmap.deletedAt.isNull()
                        }
                        .slice(User.discordId, User.email, Beatmap.id.count())
                        .select {
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

                    val avatarLocal = data.avatar?.let { downloadDiscordAvatar(it, data.id) }

                    User.update({ User.id eq sess.userId }) {
                        it[discordId] = data.id
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

    get<Verify> { req ->
        val valid = try {
            val trusted = Jwts.parserBuilder()
                .require("action", "register")
                .setSigningKey(UserCrypto.key())
                .build()
                .parseClaimsJws(req.jwt)

            trusted.body.subject.toInt().let { userId ->
                transaction {
                    User.update({
                        (User.id eq userId) and User.verifyToken.isNotNull()
                    }) {
                        it[active] = true
                        it[verifyToken] = null
                    } > 0
                }
            }
        } catch (e: SignatureException) {
            false
        } catch (e: JwtException) {
            false
        }

        call.respondRedirect("/login" + if (valid) "?valid" else "")
    }

    authenticate("auth-form") {
        post<Login> {
            call.principal<SimpleUserPrincipal>()?.let { newPrincipal ->
                val user = newPrincipal.user
                call.sessions.set(Session.fromUser(user, newPrincipal.alertCount, call = call))
            }
            call.respondRedirect("/")
        }
        post<Oauth2.Authorize> {
            call.principal<SimpleUserPrincipal>()?.let { newPrincipal ->
                val user = newPrincipal.user
                call.sessions.set(Session.fromUser(user, newPrincipal.alertCount, it.client_id, call))

                call.respondRedirect(newPrincipal.redirect)
            }
        }
    }

    get<Oauth2.AuthorizeSuccess> {
        call.sessions.get<Session>()?.let { s ->
            call.sessions.set(s.copy(oauth2ClientId = it.client_id))

            call.respondRedirect("/oauth2/authorize?" + call.request.queryString())
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

    get<Oauth2.NotMe> {
        call.sessions.clear<Session>()
        call.respondRedirect("/oauth2/authorize?" + call.request.queryString())
    }

    get<Steam> {
        val sess = call.sessions.get<Session>()
        if (sess == null) {
            call.respondRedirect("/")
            return@get
        }

        val queryParams = call.request.queryParameters as StringValues
        val claimedId = queryParams["openid.claimed_id"]

        if (claimedId == null) {
            val params = parametersOf(
                "openid.ns" to listOf("http://specs.openid.net/auth/2.0"),
                "openid.mode" to listOf("checkid_setup"),
                "openid.return_to" to listOf("${Config.siteBase()}/steam"),
                "openid.realm" to listOf(Config.siteBase()),
                "openid.identity" to listOf("http://specs.openid.net/auth/2.0/identifier_select"),
                "openid.claimed_id" to listOf("http://specs.openid.net/auth/2.0/identifier_select")
            )

            val url = URLBuilder(protocol = URLProtocol.HTTPS, host = "steamcommunity.com", pathSegments = listOf("openid", "login"), parameters = params).buildString()
            // val url = Url(URLProtocol.HTTPS, "steamcommunity.com", 0, "/openid/login", params, "", null, null, false).toString()
            call.respondRedirect(url)
        } else {
            val xml = client.submitForm(
                "https://steamcommunity.com/openid/login",
                formParameters = parametersOf(
                    "openid.ns" to listOf("http://specs.openid.net/auth/2.0"),
                    "openid.mode" to listOf("check_authentication"),
                    "openid.sig" to listOf(queryParams["openid.sig"] ?: ""),
                    *queryParams["openid.signed"]?.split(",")?.map {
                        "openid.$it" to listOf(queryParams["openid.$it"] ?: "")
                    }?.toTypedArray() ?: arrayOf()
                )
            ).bodyAsText()
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

fun Application.installOauth2() {
    install(Oauth2ServerFeature) {
        authenticationCallback = { call, callRouter ->
            if (call.request.httpMethod == HttpMethod.Get) {
                val userSession = call.sessions.get<Session>()
                val reqClientId = (call.parameters as StringValues)["client_id"]

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
            mapOf(
                "id" to it.identity?.username,
                "name" to user.uniqueName,
                "avatar" to user.avatar,
                "scopes" to it.scopes
            )
        }

        tokenEndpoint = "/api/oauth2/token"
        authorizationEndpoint = "/oauth2/authorize"
        tokenInfoEndpoint = "/api/oauth2/identity"

        identityService = object : IdentityService {
            override fun allowedScopes(forClient: Client, identity: Identity, scopes: Set<String>) = scopes

            override fun identityOf(forClient: Client, username: String) = Identity(username)

            override fun validCredentials(forClient: Client, identity: Identity, password: String) =
                transaction {
                    !User.select { (User.id eq identity.username.toInt()) and User.active }.empty()
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
    }
}
