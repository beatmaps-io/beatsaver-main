package io.beatmaps.cloudflare

import io.beatmaps.common.jsonIgnoreUnknown
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.parameters
import io.ktor.http.userAgent
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

sealed interface CaptchaConfig {
    val url: String
    val userAgent: String
    val configEnv: String
    fun enabled(secret: String): Boolean
}

data object ReCaptchaConfig : CaptchaConfig {
    override val url = "https://www.google.com/recaptcha/api/siteverify"
    override val userAgent = "Mozilla/5.0"
    override val configEnv = "RECAPTCHA_SECRET"
    override fun enabled(secret: String) = true
}

data object TurnstileConfig : CaptchaConfig {
    override val url = "https://challenges.cloudflare.com/turnstile/v0/siteverify"
    override val userAgent = "Mozilla/5.0"
    override val configEnv = "TURNSTILE_SECRET"
    override fun enabled(secret: String) = secret.startsWith("0x")
}

data object HCaptchaConfig : CaptchaConfig {
    override val url = "https://api.hcaptcha.com/siteverify"
    override val userAgent = "Mozilla/5.0"
    override val configEnv = "HCAPTCHA_SECRET"
    override fun enabled(secret: String) = true
}

@Serializable
class SiteVerifyResponse(
    val success: Boolean,
    @SerialName("challenge_ts")
    val timestamp: Instant? = null,
    val hostname: String? = null,
    val action: String? = null,
    val cdata: String? = null,
    val metadata: JsonObject? = null,

    @SerialName("error-codes")
    val errorCodes: List<String> = emptyList()
)

class CaptchaVerifier(val config: CaptchaConfig) {
    private val secret = System.getenv(config.configEnv) ?: throw IllegalArgumentException("Captcha config missing")

    fun enabled() = config.enabled(secret)

    suspend fun verify(client: HttpClient, gRecaptchaResponse: String, remoteIp: String): SiteVerifyResponse {
        val txt = client.submitForm(
            config.url,
            parameters {
                append("secret", secret)
                append("response", gRecaptchaResponse)
                append("remoteip", remoteIp)
            }
        ) {
            userAgent(config.userAgent)
        }.bodyAsText()
        return jsonIgnoreUnknown.decodeFromString<SiteVerifyResponse>(txt)
    }
}
