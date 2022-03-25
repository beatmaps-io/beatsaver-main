@file:UseSerializers(InstantAsStringSerializer::class)
package io.beatmaps.api

import io.beatmaps.common.IModLogOpAction
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

enum class AccountType {
    DISCORD, SIMPLE, DUAL
}

@Serializable
data class UserDetail(
    val id: Int,
    val name: String,
    val uniqueSet: Boolean = true,
    val hash: String? = null,
    val testplay: Boolean? = null,
    val avatar: String,
    val stats: UserStats? = null,
    val type: AccountType,
    val email: String? = null,
    val uploadLimit: Int? = null,
    val curator: Boolean? = null,
    val verifiedMapper: Boolean = false
) { companion object }
@Serializable
data class UserStats(
    val totalUpvotes: Int,
    val totalDownvotes: Int,
    val totalMaps: Int,
    val rankedMaps: Int,
    val avgBpm: Float,
    val avgScore: Float,
    val avgDuration: Float,
    val firstUpload: Instant?,
    val lastUpload: Instant?,
    val diffStats: UserDiffStats? = null
)
@Serializable
data class UserDiffStats(val total: Int, val easy: Int, val normal: Int, val hard: Int, val expert: Int, val expertPlus: Int)
@Serializable
data class BeatsaverLink(val linked: Boolean) { companion object }
@Serializable
data class BeatsaverLinkReq(val user: String, val password: String, val useOldName: Boolean = true)
@Serializable
data class Alert(val map: MapDetail, val time: Instant, val action: IModLogOpAction) { companion object }
@Serializable
data class UsernameReq(val username: String)
@Serializable
data class RegisterRequest(val captcha: String, val username: String, val email: String, val password: String, val password2: String)
@Serializable
data class ActionResponse(val success: Boolean, val errors: List<String> = listOf())
@Serializable
data class ForgotRequest(val captcha: String, val email: String)
@Serializable
data class ResetRequest(val jwt: String, val password: String, val password2: String)
@Serializable
data class AccountRequest(val currentPassword: String? = null, val password: String? = null, val password2: String? = null)
@Serializable
data class UserAdminRequest(val userId: Int, val maxUploadSize: Int, val curator: Boolean, val verifiedMapper: Boolean)
