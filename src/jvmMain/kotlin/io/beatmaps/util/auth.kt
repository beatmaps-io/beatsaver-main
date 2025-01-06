package io.beatmaps.util

import io.beatmaps.api.OauthScope
import io.beatmaps.cloudflare.CaptchaProvider
import io.beatmaps.cloudflare.CaptchaVerifier
import io.beatmaps.cloudflare.SiteVerifyResponse
import io.beatmaps.common.dbo.UserDao
import io.beatmaps.login.Session
import io.beatmaps.login.server.DBTokenStore
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.origin
import io.ktor.server.response.respond
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.myndocs.oauth2.token.AccessToken

enum class AuthType {
    None, Session, Oauth
}

private fun sessionFromToken(token: AccessToken) = (token.identity?.metadata?.get("object") as? UserDao)
    ?.let { Session.fromUser(it, oauth2ClientId = token.clientId) }

suspend fun <T> ApplicationCall.optionalAuthorization(scope: OauthScope? = null, block: suspend ApplicationCall.(AuthType, Session?) -> T) {
    // Oauth
    checkOauthHeader(scope)?.let(::sessionFromToken)?.also { block(AuthType.Oauth, it) }
        // Session
        ?: sessions.get<Session>()?.also { block(AuthType.Session, it) }
        // Fallback
        ?: run { block(AuthType.None, null) }
}

suspend fun <T> PipelineContext<*, ApplicationCall>.optionalAuthorization(scope: OauthScope? = null, block: suspend PipelineContext<*, ApplicationCall>.(AuthType, Session?) -> T) {
    val that = this
    call.optionalAuthorization(scope) { a, b ->
        block(that, a, b)
    }
}

suspend fun <T> PipelineContext<*, ApplicationCall>.requireAuthorization(scope: OauthScope? = null, block: suspend PipelineContext<*, ApplicationCall>.(AuthType, Session) -> T) {
    optionalAuthorization(scope) { type, sess ->
        if (type == AuthType.None || sess == null) {
            call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
        } else {
            block(type, sess)
        }
    }
}

fun ApplicationCall.checkOauthHeader(scope: OauthScope? = null) =
    request.parseAuthorizationHeader().let { authHeader ->
        if (authHeader is HttpAuthHeader.Single) {
            val token = DBTokenStore.accessToken(authHeader.blob)

            when (token?.expired()) {
                true -> DBTokenStore.revokeAccessToken(token.accessToken).let { null }
                false -> {
                    if (token.scopes.contains(scope?.tag)) {
                        token
                    } else { null }
                }
                null -> null
            }
        } else { null }
    }

suspend fun <T> PipelineContext<*, ApplicationCall>.captchaProvider(block: suspend (CaptchaProvider) -> T): T = block(CaptchaVerifier.provider(call))

suspend fun <T> PipelineContext<*, ApplicationCall>.requireCaptcha(client: HttpClient, captcha: String?, block: suspend PipelineContext<*, ApplicationCall>.() -> T, error: (suspend PipelineContext<*, ApplicationCall>.(SiteVerifyResponse) -> T)? = null) =
    captchaProvider { provider ->
        withContext(Dispatchers.IO) {
            CaptchaVerifier.verify(client, provider, captcha ?: "", call.request.origin.remoteHost)
        }.let { result ->
            if (result.success) {
                block()
            } else {
                error?.invoke(this, result) ?: throw BadRequestException("Bad captcha")
            }
        }
    }

suspend fun <T> PipelineContext<*, ApplicationCall>.captchaIfPresent(client: HttpClient, captcha: String?, block: suspend PipelineContext<*, ApplicationCall>.() -> T) =
    if (captcha != null) {
        this.requireCaptcha(client, captcha, block)
    } else {
        block()
    }
