@file:UseSerializers(InstantAsStringSerializer::class)

package io.beatmaps.api

import io.beatmaps.common.api.EIssueType
import io.beatmaps.common.api.IDbIssueData
import io.beatmaps.common.api.IIssueData
import io.beatmaps.common.api.MapReportDataBase
import io.beatmaps.common.api.PlaylistReportDataBase
import io.beatmaps.common.api.ReviewReportDataBase
import io.beatmaps.common.api.UserReportDataBase
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
    val data: IHydratedIssueData? = null,
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
data class HydratedMapReportData(val map: MapDetail) : IHydratedIssueData, MapReportDataBase() {
    override val mapId = map.id
}

@Serializable
@SerialName("PlaylistReport")
data class HydratedPlaylistReportData(val playlist: PlaylistFull) : IHydratedIssueData, PlaylistReportDataBase() {
    override val playlistId = playlist.playlistId
}

@Serializable
@SerialName("UserReport")
data class HydratedUserReportData(val user: UserDetail) : IHydratedIssueData, UserReportDataBase() {
    override val userId = user.id
}

@Serializable
@SerialName("ReviewReport")
data class HydratedReviewReportData(val review: ReviewDetail) : IHydratedIssueData, ReviewReportDataBase() {
    override val reviewId = review.id
}

@Serializable
data class IssueCreationRequest(val captcha: String, val text: String, val data: IDbIssueData)

@Serializable
data class IssueUpdateRequest(val closed: Boolean)

@Serializable
data class IssueCommentRequest(val captcha: String? = null, val text: String? = null, val public: Boolean? = null)
