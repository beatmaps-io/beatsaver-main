@file:UseSerializers(InstantAsStringSerializer::class)

package io.beatmaps.api

import io.beatmaps.common.solr.SearchInfo
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

enum class AccountType {
    DISCORD, SIMPLE, DUAL
}

class UserConstants {
    companion object {
        const val MAX_DESCRIPTION_LENGTH = 500
    }
}

enum class PatreonTier(val pledge: Int, val supporting: Boolean, val title: String, val maxWips: Int) {
    None(0, false, "", 10), Supporter(350, true, "Supporter", 20), SupporterPlus(1000, true, "Supporter+", 50);

    companion object {
        const val maxWipsMessage = "Too many unpublished maps. Either delete or publish your existing maps. To gain additional unpublished slots become a site supporter."
        fun fromPledge(pledge: Int) = entries.filter { it.pledge >= pledge }.minBy { it.pledge }
    }
}

@Serializable
data class UserDetail(
    val id: Int,
    val name: String,
    val description: String? = null,
    val uniqueSet: Boolean = true,
    val hash: String? = null,
    val testplay: Boolean? = null,
    val avatar: String,
    val stats: UserStats? = null,
    val followData: UserFollowData? = null,
    val type: AccountType,
    val email: String? = null,
    val uploadLimit: Int? = null,
    val admin: Boolean? = null,
    val curator: Boolean? = null,
    val seniorCurator: Boolean? = null,
    val curatorTab: Boolean = false,
    val verifiedMapper: Boolean = false,
    val suspendedAt: Instant? = null,
    val playlistUrl: String? = null,
    val patreon: PatreonTier? = null
) {
    fun profileLink(tab: String? = null, absolute: Boolean = false) = UserDetailHelper.profileLink(this, tab, absolute)
    companion object
}

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object UserDetailHelper {
    fun profileLink(userDetail: UserDetail, tab: String?, absolute: Boolean): String
}

@Serializable
data class UserStats(
    val totalUpvotes: Int = 0,
    val totalDownvotes: Int = 0,
    val totalMaps: Int = 0,
    val rankedMaps: Int = 0,
    val avgBpm: Float = 0f,
    val avgScore: Float = 0f,
    val avgDuration: Float = 0f,
    val firstUpload: Instant? = null,
    val lastUpload: Instant? = null,
    val diffStats: UserDiffStats? = null
)

@Serializable
data class UserFollowData(
    val followers: Int,
    val follows: Int?,
    val following: Boolean,
    val upload: Boolean,
    val curation: Boolean,
    val collab: Boolean
)

@Serializable
data class UserDiffStats(val total: Int, val easy: Int, val normal: Int, val hard: Int, val expert: Int, val expertPlus: Int)

@Serializable
data class AccountDetailReq(val textContent: String)

@Serializable
data class RegisterRequest(val captcha: String, val username: String, val email: String, val password: String, val password2: String)

@Serializable
data class ForgotRequest(val captcha: String, val email: String)

@Serializable
data class EmailRequest(val captcha: String, val email: String)

@Serializable
data class ResetRequest(val jwt: String, val password: String, val password2: String)

@Serializable
data class ChangeEmailRequest(val jwt: String, val password: String)

@Serializable
data class AccountRequest(val currentPassword: String? = null, val password: String? = null, val password2: String? = null)

@Serializable
data class UserAdminRequest(val userId: Int, val maxUploadSize: Int, val curator: Boolean, val seniorCurator: Boolean, val curatorTab: Boolean, val verifiedMapper: Boolean) {
    companion object {
        val allowedUploadSizes = arrayOf(0, 15, 30, 50)
    }
}

@Serializable
data class UserSuspendRequest(val userId: Int, val suspended: Boolean, val reason: String?)

@Serializable
data class UserFollowRequest(val userId: Int, val following: Boolean, val upload: Boolean, val curation: Boolean, val collab: Boolean)

@Serializable
data class SessionRevokeRequest(val userId: Int? = null, val site: Boolean? = null, val reason: String? = null)

@Serializable
data class UserSearchResponse(override val docs: List<UserDetail>, override val info: SearchInfo? = null) : GenericSearchResponse<UserDetail>
