@file:UseSerializers(InstantAsStringSerializer::class)

package io.beatmaps.api

import io.beatmaps.common.api.EIssueType
import io.beatmaps.common.api.IDbIssueData
import io.beatmaps.common.api.IIssueData
import io.beatmaps.common.api.IMapReportData
import io.beatmaps.common.api.IUserReportData
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

class IssueConstants {
    companion object {
        const val MAX_COMMENT_LENGTH = 2000
    }
}

@Serializable
data class IssueDetail(
    val id: Int,
    val type: EIssueType,
    val creator: UserDetail,
    val createdAt: Instant,
    val closedAt: Instant? = null,
    val data: IHydratedIssueData,
    val comments: List<IssueCommentDetail>? = null
) {
    companion object
}

@Serializable
data class IssueCommentDetail(
    val id: Int,
    val user: UserDetail,
    val public: Boolean,
    val text: String,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object
}

@Serializable
sealed interface IHydratedIssueData : IIssueData

@Serializable
@SerialName("MapReport")
data class HydratedMapReportData(val map: MapDetail) : IHydratedIssueData, IMapReportData {
    override val typeEnum = EIssueType.MapReport
    override val mapId = map.id
}

@Serializable
@SerialName("UserReport")
data class HydratedUserReportData(val user: UserDetail) : IHydratedIssueData, IUserReportData {
    override val typeEnum = EIssueType.UserReport
    override val userId = user.id
}

@Serializable
data class IssueCreationRequest(val captcha: String, val text: String, val data: IDbIssueData)

@Serializable
data class IssueUpdateRequest(val closed: Boolean)

@Serializable
data class IssueCommentRequest(val captcha: String? = null, val text: String? = null, val public: Boolean? = null)
