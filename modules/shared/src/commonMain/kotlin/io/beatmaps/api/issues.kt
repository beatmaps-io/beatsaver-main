@file:UseSerializers(InstantAsStringSerializer::class)

package io.beatmaps.api

import io.beatmaps.common.api.BasicMapInfo
import io.beatmaps.common.api.BasicPlaylistInfo
import io.beatmaps.common.api.BasicReviewInfo
import io.beatmaps.common.api.BasicUserInfo
import io.beatmaps.common.api.DbMapReportData
import io.beatmaps.common.api.DbPlaylistReportData
import io.beatmaps.common.api.DbReviewReportData
import io.beatmaps.common.api.DbUserReportData
import io.beatmaps.common.api.EIssueType
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.api.EPlaylistType
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

fun BasicUserInfo.detail(current: UserDetail? = null) = UserDetail(
    id,
    name,
    description,
    verifiedMapper = current?.verifiedMapper ?: false,
    avatar = avatar,
    type = AccountType.SIMPLE
)

fun BasicMapInfo.detail(current: MapDetail? = null) = MapDetail(
    key,
    name,
    description,
    mapper.detail(current?.uploader),
    MapDetailMetadata.default.copy(bpm = bpm, duration = duration),
    MapStats.default,
    uploaded,
    automapper = current?.automapper ?: false,
    ranked = current?.ranked ?: false,
    qualified = current?.qualified ?: false,
    createdAt = Instant.DISTANT_PAST,
    updatedAt = Instant.DISTANT_PAST,
    declaredAi = declaredAi,
    blRanked = false,
    blQualified = false,
    versions = listOf(
        current?.versions?.firstOrNull { it.hash == hash } ?: MapVersion(
            hash,
            null,
            EMapState.Uploaded,
            Instant.DISTANT_PAST,
            downloadURL = "",
            coverURL = "",
            previewURL = ""
        )
    )
)

fun BasicReviewInfo.detail(current: ReviewDetail? = null) = ReviewDetail(
    id,
    creator.detail(current?.creator),
    map = map.detail(current?.map),
    text = text,
    sentiment = sentiment,
    createdAt = createdAt,
    updatedAt = Instant.DISTANT_PAST,
    replies = emptyList()
)

fun BasicPlaylistInfo.detail(current: PlaylistFull? = null) = PlaylistFull(
    id,
    name,
    description,
    playlistImage = current?.playlistImage ?: "",
    playlistImage512 = current?.playlistImage512 ?: "",
    owner = owner.detail(current?.owner),
    createdAt = current?.createdAt ?: Instant.DISTANT_PAST,
    updatedAt = Instant.DISTANT_PAST,
    songsChangedAt = Instant.DISTANT_PAST,
    downloadURL = current?.downloadURL ?: "",
    type = EPlaylistType.Public
)

@Serializable
@SerialName("MapReport")
data class HydratedMapReportData(
    val map: MapDetail?,
    override val mapId: String,
    override val snapshot: BasicMapInfo?
) : IHydratedIssueData, MapReportDataBase() {
    constructor(map: MapDetail?, base: DbMapReportData) : this(map, base.mapId, base.snapshot)
    fun detail() = snapshot?.detail(map)
}

@Serializable
@SerialName("PlaylistReport")
data class HydratedPlaylistReportData(
    val playlist: PlaylistFull?,
    override val playlistId: Int,
    override val snapshot: BasicPlaylistInfo?
) : IHydratedIssueData, PlaylistReportDataBase() {
    constructor(playlist: PlaylistFull?, base: DbPlaylistReportData) : this(playlist, base.playlistId, base.snapshot)
    fun detail() = snapshot?.detail(playlist)
}

@Serializable
@SerialName("UserReport")
data class HydratedUserReportData(val user: UserDetail?, override val userId: Int, override val snapshot: BasicUserInfo?) : IHydratedIssueData, UserReportDataBase() {
    constructor(user: UserDetail?, base: DbUserReportData) : this(user, base.userId, base.snapshot)
    fun detail() = snapshot?.detail(user)
}

@Serializable
@SerialName("ReviewReport")
data class HydratedReviewReportData(
    val review: ReviewDetail?,
    override val reviewId: Int,
    override val snapshot: BasicReviewInfo?
) : IHydratedIssueData, ReviewReportDataBase() {
    constructor(review: ReviewDetail?, base: DbReviewReportData) : this(review, base.reviewId, base.snapshot)
    fun detail() = snapshot?.detail(review)
}

@Serializable
data class IssueCreationRequest(val captcha: String, val text: String, val id: Int, val type: EIssueType)

@Serializable
data class IssueUpdateRequest(val closed: Boolean)

@Serializable
data class IssueCommentRequest(val captcha: String? = null, val text: String? = null, val public: Boolean? = null)
