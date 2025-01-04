package io.beatmaps.cloudflare

import io.beatmaps.api.ActionResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.http.parameters
import io.ktor.http.userAgent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.sessions.sessionId
import io.ktor.util.AttributeKey
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.util.logging.Logger
import kotlin.random.Random

enum class CaptchaProvider(val url: String = "", val userAgent: String = "", private val envPrefix: String = "", val enabled: (String) -> Boolean = { true }, val defaultWeight: Int = 100) {
    ReCaptcha(
        "https://www.google.com/recaptcha/api/siteverify",
        "Mozilla/5.0",
        "RECAPTCHA"
    ),
    Turnstile(
        "https://challenges.cloudflare.com/turnstile/v0/siteverify",
        "Mozilla/5.0",
        "TURNSTILE",
        { it.startsWith("0x") }
    ),
    HCaptcha(
        "https://api.hcaptcha.com/siteverify",
        "Mozilla/5.0",
        "HCAPTCHA"
    ),
    Fake(defaultWeight = 0);

    fun configEnv() = "${envPrefix}_SECRET"
    fun weightEnv() = "${envPrefix}_WEIGHT"
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
) {
    fun toActionResponse() = if (success) {
        ActionResponse.success()
    } else {
        ActionResponse.error(*errorCodes.map { e -> "Captcha error: $e" }.toTypedArray())
    }
}

object CaptchaVerifier {
    private val logger = Logger.getLogger("bmio.CaptchaVerifier")
    private val captchaProviderAttr = AttributeKey<CaptchaProvider>("captchaProvider")

    private val secrets = CaptchaProvider.entries.mapNotNull { config ->
        (if (config.configEnv().length == 7) "" else System.getenv(config.configEnv()))?.let { config to it }
    }.toMap()

    private val cdf = secrets.keys.map { config ->
        config to (System.getenv(config.weightEnv())?.toIntOrNull() ?: config.defaultWeight)
    }.let { weights ->
        val totalWeight = weights.sumOf { it.second }.toFloat()
        if (totalWeight == 0f) {
            listOf(weights.first().first to 1f)
        } else {
            weights.fold(0f to emptyList<Pair<CaptchaProvider, Float>>()) { (acc, list), next ->
                val thisWeight = next.second / totalWeight
                val cumulativeWeight = acc + thisWeight
                cumulativeWeight to list.plus(next.first to cumulativeWeight)
            }.second
        }
    }

    private fun pickProvider(call: ApplicationCall) =
        if (cdf.size == 1) {
            cdf.first()
        } else {
            Random(call.sessionId.hashCode()).nextFloat().let { nextRandom ->
                cdf.first { it.second > nextRandom }
            }
        }.first

    fun provider(call: ApplicationCall): CaptchaProvider {
        if (!call.attributes.contains(captchaProviderAttr)) {
            call.attributes.put(captchaProviderAttr, pickProvider(call))
        }

        return call.attributes[captchaProviderAttr]
    }
    fun enabled(provider: CaptchaProvider) = secrets[provider]?.let { provider.enabled(it) } ?: false

    suspend fun verify(client: HttpClient, provider: CaptchaProvider, gRecaptchaResponse: String, remoteIp: String) =
        if (provider == CaptchaProvider.Fake) {
            logger.warning("ReCAPTCHA not setup. Allowing request anyway")
            SiteVerifyResponse(true)
        } else {
            val secret = secrets[provider] ?: ""
            client.submitForm(
                provider.url,
                parameters {
                    append("secret", secret)
                    append("response", gRecaptchaResponse)
                    append("remoteip", remoteIp)
                }
            ) {
                userAgent(provider.userAgent)
            }.body<SiteVerifyResponse>()
        }
}
