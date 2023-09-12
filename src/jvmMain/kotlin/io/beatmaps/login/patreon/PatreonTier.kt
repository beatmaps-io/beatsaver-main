package io.beatmaps.login.patreon

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

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
