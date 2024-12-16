package io.beatmaps.login.patreon

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

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
    @JsonNames("is_free_trial")
    val isFreeTrial: Boolean? = null,
    @JsonNames("is_gifted")
    val isGifted: Boolean? = null,
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
            "campaign_lifetime_support_cents", "currently_entitled_amount_cents", "email", "full_name", "is_follower", "is_free_trial", "last_charge_date", "last_charge_status",
            "lifetime_support_cents", "next_charge_date", "note", "patron_status", "pledge_cadence", "pledge_relationship_start", "will_pay_amount_cents", "is_gifted"
        )
    }
}
