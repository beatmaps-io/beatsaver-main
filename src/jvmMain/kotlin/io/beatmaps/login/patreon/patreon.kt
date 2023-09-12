package io.beatmaps.login.patreon

import io.beatmaps.api.requireAuthorization
import io.beatmaps.common.client
import io.beatmaps.common.db.upsert
import io.beatmaps.common.dbo.Patreon
import io.beatmaps.common.dbo.User
import io.beatmaps.common.json
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.OAuthAccessTokenResponse
import io.ktor.server.auth.OAuthServerSettings
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.locations.Location
import io.ktor.server.locations.get
import io.ktor.server.locations.post
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.util.hex
import kotlinx.datetime.toJavaInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.logging.Logger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private val patreonLogger = Logger.getLogger("bmio.Patreon")

private val patreonWebhookSecret = System.getenv("PATREON_WEBHOOK_SECRET") ?: "insecure-secret"

val patreonProvider = OAuthServerSettings.OAuth2ServerSettings(
    name = "patreon",
    authorizeUrl = "https://patreon.com/oauth2/authorize",
    accessTokenUrl = "https://patreon.com/api/oauth2/token",
    clientId = System.getenv("PATREON_CLIENTID") ?: "",
    clientSecret = System.getenv("PATREON_CLIENTSECRET") ?: "",
    requestMethod = HttpMethod.Post,
    defaultScopes = listOf("identity")
)

@Location("/patreon")
class PatreonLink

@Location("/patreon/hook")
class PatreonHook

@Serializable
data class PatreonResponse(
    val data: JsonElement,
    val included: List<JsonElement>? = null,
    val links: JsonObject
) {
    inline fun <reified T : PatreonObject> getIncluded(fields: PatreonFields) =
        included.orEmpty().plus(data).filter { inc -> inc.jsonObject["type"]?.jsonPrimitive?.content == fields.fieldKey }
            .map { json.decodeFromJsonElement<PatreonIncluded<T>>(it) }
}

@Serializable
data class PatreonIncluded<T>(
    override val id: String,
    override val type: String,
    val attributes: T,
    val relationships: Map<String, PatreonRelationship>? = null
) : PatreonBase where T : PatreonObject

@Serializable
data class PatreonRelationship(
    val data: JsonElement? = null,
    val links: JsonObject? = null
)

interface PatreonBase {
    val id: String
    val type: String
}

sealed interface PatreonObject

enum class PatreonStatus {
    @JsonNames("active_patron")
    ACTIVE,

    @JsonNames("declined_patron")
    DECLINED,

    @JsonNames("former_patron")
    FORMER
}

enum class LastChargeStatus {
    Paid, Declined, Deleted, Pending, Refunded, Fraud, Other
}

abstract class PatreonFields {
    abstract val fieldKey: String
    abstract val fields: List<String>

    override fun toString() =
        "fields%5B$fieldKey%5D=${fields.joinToString(",")}"
}

fun Route.patreonLink() {
    authenticate("patreon") {
        get<PatreonLink> {
            requireAuthorization { sess ->
                val principal = call.authentication.principal<OAuthAccessTokenResponse.OAuth2>() ?: error("No principal")

                val include = listOf("memberships", "memberships.currently_entitled_tiers")
                val fields = listOf(PatreonTier, PatreonMembership).joinToString("&")

                val responseText = client.get("https://patreon.com/api/oauth2/v2/identity?include=${include.joinToString(",")}&$fields") {
                    header("Authorization", "Bearer ${principal.accessToken}")
                }.bodyAsText()

                val response = json.decodeFromString<PatreonResponse>(responseText)
                val membership = response.getIncluded<PatreonMembership>(PatreonMembership).firstOrNull()
                val user = response.getIncluded<PatreonUser>(PatreonUser).first()
                val tierObj = response.getIncluded<PatreonTier>(PatreonTier).maxByOrNull { it.attributes.amountCents ?: Int.MIN_VALUE }

                patreonLogger.info(membership.toString())
                patreonLogger.info(tierObj.toString())

                transaction {
                    User.update({ User.id eq sess.userId }) {
                        it[patreonId] = user.id.toInt()
                    }

                    Patreon.upsert(Patreon.id) {
                        it[id] = user.id.toInt()
                        it[pledge] = membership?.attributes?.currentlyEntitledAmountCents
                        it[active] = membership?.attributes?.patronStatus == PatreonStatus.ACTIVE
                        it[expireAt] = membership?.attributes?.nextChargeDate?.toJavaInstant()
                        it[tier] = tierObj?.id?.toIntOrNull()
                    }
                }
            }
        }
    }

    fun signature(json: String, secret: String): String {
        val signingKey = SecretKeySpec(secret.toByteArray(), "HmacMD5")
        val mac = Mac.getInstance("HmacMD5")
        mac.init(signingKey)

        return hex(mac.doFinal(json.toByteArray()))
    }

    post<PatreonHook> {
        val hookContent = call.receiveText()

        val signature = call.request.header("X-Patreon-Signature")
        val event = call.request.header("X-Patreon-Event")

        if (signature != signature(hookContent, patreonWebhookSecret)) {
            throw BadRequestException("Invalid signature")
        }

        patreonLogger.info("Received patreon webhook $event")
        patreonLogger.info(hookContent)

        val hook = json.decodeFromString<PatreonResponse>(hookContent)
        val user = hook.getIncluded<PatreonUser>(PatreonUser).first()
        val membership = hook.getIncluded<PatreonMembership>(PatreonMembership).first()
        val tierObj = hook.getIncluded<PatreonTier>(PatreonTier).maxByOrNull { it.attributes.amountCents ?: Int.MIN_VALUE }

        transaction {
            Patreon.upsert(Patreon.id) {
                it[id] = user.id.toInt()
                it[pledge] = membership.attributes.currentlyEntitledAmountCents
                it[active] = membership.attributes.patronStatus == PatreonStatus.ACTIVE
                it[expireAt] = membership.attributes.nextChargeDate?.toJavaInstant()
                it[tier] = tierObj?.id?.toIntOrNull()
            }
        }

        call.respond(HttpStatusCode.OK)
    }
}
