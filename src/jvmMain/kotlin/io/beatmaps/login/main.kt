package io.beatmaps.login

import de.nielsfalk.ktor.swagger.Ignore
import io.beatmaps.api.user.UserCrypto
import io.beatmaps.common.Config
import io.beatmaps.common.amqp.pub
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.UserDao
import io.beatmaps.common.getCountry
import io.beatmaps.genericPage
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.SignatureException
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.parametersOf
import io.ktor.resources.Resource
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.plugins.origin
import io.ktor.server.request.queryString
import io.ktor.server.request.uri
import io.ktor.server.request.userAgent
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.util.StringValues
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

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
    val countryCode: String? = null,
    val blurnsfw: Boolean = true
) {
    fun isAdmin() = admin
    fun isCurator() = isAdmin() || curator

    companion object {
        fun fromUser(user: UserDao, alertCount: Int? = null, oauth2ClientId: String? = null, call: ApplicationCall? = null) = Session(
            user.id.value, user.hash, user.email, user.name, user.testplay, user.steamId, user.oculusId, user.admin, user.uniqueName, false, alertCount,
            user.curator, oauth2ClientId, user.suspendedAt != null, call?.request?.origin?.remoteHost, call?.request?.userAgent(),
            call?.getCountry()?.let { if (it.success) it.countryCode else null }, user.blurnsfw
        )
    }
}

@Resource("/login")
class Login

@Resource("/oauth2")
class Oauth2 {
    @Resource("/authorize")
    class Authorize(
        @SerialName("client_id")
        val clientId: String,
        @Ignore
        val api: Oauth2
    )

    @Resource("/authorize/success")
    class AuthorizeSuccess(
        @SerialName("client_id")
        val clientId: String,
        @Ignore
        val api: Oauth2
    )

    @Resource("/authorize/not-me")
    class NotMe(
        @Ignore
        val api: Oauth2
    )
}

@Resource("/quest")
class Quest {
    @Resource("")
    class CodeLogin(
        @Ignore
        val api: Quest
    )

    @Resource("/not-me")
    class NotMe(
        @Ignore
        val api: Quest
    )
}

@Resource("/register")
class Register

@Resource("/forgot")
class Forgot

@Resource("/reset/{jwt}")
class Reset(val jwt: String)

@Resource("/verify/{jwt}")
data class Verify(
    val jwt: String
)

@Resource("/username")
class Username

@Resource("/steam")
class Steam

fun Route.authRoute(client: HttpClient) {
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
                        it[updatedAt] = NowExpression(updatedAt)
                    } > 0
                }.also {
                    if (it) call.pub("beatmaps", "user.$userId.updated.active", null, userId)
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
                call.sessions.set(Session.fromUser(user, newPrincipal.alertCount, it.clientId, call))

                call.respondRedirect(newPrincipal.redirect)
            }
        }
        post<Quest.CodeLogin> {
            call.principal<SimpleUserPrincipal>()?.let { newPrincipal ->
                val user = newPrincipal.user
                call.sessions.set(Session.fromUser(user, newPrincipal.alertCount, call = call))

                call.respondRedirect(call.request.uri)
            }
        }
    }

    get<Oauth2.AuthorizeSuccess> {
        call.sessions.get<Session>()?.let { s ->
            call.sessions.set(s.copy(oauth2ClientId = it.clientId))

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

    get<Quest.CodeLogin> {
        genericPage()
    }

    get<Quest.NotMe> {
        call.sessions.clear<Session>()
        call.respondRedirect("/quest?" + call.request.queryString())
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
                    it[updatedAt] = NowExpression(updatedAt)
                }
            }
            call.sessions.set(sess.copy(steamId = steamid))
            call.respondRedirect("/profile")
        }
    }
}
