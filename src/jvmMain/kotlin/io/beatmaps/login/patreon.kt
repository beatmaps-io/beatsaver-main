package io.beatmaps.login

import io.beatmaps.common.client
import io.beatmaps.common.json
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
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
import io.ktor.server.routing.Route
import io.ktor.util.hex
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    val included: List<JsonElement>,
    val links: JsonObject
) {
    fun <T : PatreonObject> getIncluded(fields: PatreonFields) =
        included.filter { inc -> inc.jsonObject["type"]?.jsonPrimitive?.content == fields.fieldKey }
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
    val data: List<PatreonRelationshipInfo>
)

@Serializable
data class PatreonRelationshipInfo(
    override val id: String,
    override val type: String
) : PatreonBase

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

@Serializable
data class PatreonMembership(
    @JsonNames("campaign_lifetime_support_cents")
    val campaignLifetimeSupportCents: Int? = null,
    @JsonNames("currently_entitled_amount_cents")
    val currentlyEntitledAmountCents: Int? = null,
    val email: String? = null,
    @JsonNames("full_name")
    val fullName: String? = null,
    @JsonNames("is_follower")
    val isFollower: Boolean? = null,
    @JsonNames("last_charge_date")
    val lastChargeDate: Instant? = null,
    @JsonNames("last_charge_status")
    val lastChargeStatus: LastChargeStatus? = null,
    @JsonNames("lifetime_support_cents")
    val lifetimeSupportCents: Int? = null,
    @JsonNames("next_charge_date")
    val nextChargeDate: Instant? = null,
    val note: String? = null,
    @JsonNames("patron_status")
    val patronStatus: PatreonStatus? = null,
    @JsonNames("pledge_cadence")
    val pledgeCadence: Int? = null,
    @JsonNames("pledge_relationship_start")
    val pledgeRelationshipStart: Instant? = null,
    @JsonNames("will_pay_amount_cents")
    val willPayAmountCents: Int? = null
) : PatreonObject {
    companion object : PatreonFields() {
        override val fieldKey = "member"
        override val fields = listOf(
            "campaign_lifetime_support_cents", "currently_entitled_amount_cents", "email", "full_name", "is_follower", "last_charge_date", "last_charge_status",
            "lifetime_support_cents", "next_charge_date", "note", "patron_status", "pledge_cadence", "pledge_relationship_start", "will_pay_amount_cents"
        )
    }
}

@Serializable
data class PatreonTier(
    @JsonNames("amount_cents")
    val amountCents: Int? = null,
    @JsonNames("created_at")
    val createdAt: Instant? = null,
    val description: String? = null,
    @JsonNames("discord_role_ids")
    val discordRoleIds: List<String>? = null,
    @JsonNames("edited_at")
    val editedAt: Instant? = null,
    @JsonNames("image_url")
    val imageUrl: String? = null,
    @JsonNames("patron_count")
    val patronCount: Int? = null,
    @JsonNames("post_count")
    val postCount: Int? = null,
    @JsonNames("published")
    val published: Boolean? = null,
    @JsonNames("published_at")
    val publishedAt: Instant? = null,
    val remaining: Int? = null,
    @JsonNames("requires_shipping")
    val requiresShipping: Boolean? = null,
    val title: String? = null,
    @JsonNames("unpublished_at")
    val unpublishedAt: Instant? = null,
    val url: String? = null,
    @JsonNames("user_limit")
    val userLimit: Int? = null
) : PatreonObject {
    companion object : PatreonFields() {
        override val fieldKey = "tier"
        override val fields = listOf(
            "amount_cents", "created_at", "description", "discord_role_ids", "edited_at", "image_url", "patron_count", "post_count",
            "published", "published_at", "remaining", "requires_shipping", "title", "unpublished_at", "url", "user_limit"
        )
    }
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
            val principal = call.authentication.principal<OAuthAccessTokenResponse.OAuth2>() ?: error("No principal")

            val include = listOf("memberships", "memberships.currently_entitled_tiers")
            val fields = listOf(PatreonTier, PatreonMembership).joinToString("&")

            val responseText = client.get("https://patreon.com/api/oauth2/v2/identity?include=${include.joinToString(",")}&$fields") {
                header("Authorization", "Bearer ${principal.accessToken}")
            }.bodyAsText()

            val response = json.decodeFromString<PatreonResponse>(responseText)
            val membership = response.getIncluded<PatreonMembership>(PatreonMembership).first()
            val tier = response.getIncluded<PatreonTier>(PatreonTier).first()

            println(membership)
            println(tier)
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

        // val hook = json.decodeFromString<PatreonResponse>(hookContent)
    }
}
