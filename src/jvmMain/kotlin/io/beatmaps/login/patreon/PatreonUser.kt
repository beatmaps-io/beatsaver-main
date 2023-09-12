package io.beatmaps.login.patreon

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject

@Serializable
data class PatreonUser(
    val about: String? = null,
    @JsonNames("can_see_nsfw")
    val canSeeNSFW: Boolean? = null,
    val created: Instant? = null,
    val email: String? = null,
    @JsonNames("first_name")
    val firstName: String? = null,
    @JsonNames("full_name")
    val fullName: String? = null,
    @JsonNames("hide_pledges")
    val hidePledges: Boolean? = null,
    @JsonNames("image_url")
    val imageUrl: String? = null,
    @JsonNames("is_email_verified")
    val isEmailVerified: Boolean? = null,
    @JsonNames("last_name")
    val last_name: String? = null,
    @JsonNames("like_count")
    val likeCount: Int? = null,
    @JsonNames("social_connections")
    val socialConnections: JsonObject? = null,
    @JsonNames("thumb_url")
    val thumbUrl: String? = null,
    val url: String? = null,
    val vanity: String? = null
) : PatreonObject {
    companion object : PatreonFields() {
        override val fieldKey = "user"
        override val fields = listOf(
            "about", "can_see_nsfw", "created", "email", "first_name", "full_name", "hide_pledges", "image_url",
            "is_email_verified", "last_name", "like_count", "social_connections", "thumb_url", "url", "vanity"
        )
    }
}
