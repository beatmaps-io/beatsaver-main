package io.beatmaps.api

import io.beatmaps.common.api.ReviewSentiment
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

class ReviewConstants {
    companion object {
        const val MAX_LENGTH = 2000
        const val MAX_REPLY_LENGTH = 1000
        const val MINIMUM_REVIEWS = 5
    }
}

interface CommentDetail {
    val id: Int
    val creator: UserDetail?
    val text: String
    val createdAt: Instant
    val updatedAt: Instant
    val deletedAt: Instant?
}

@Serializable
data class ReviewDetail(
    override val id: Int,
    override val creator: UserDetail? = null,
    val map: MapDetail? = null,
    override val text: String,
    val sentiment: ReviewSentiment,
    override val createdAt: Instant,
    override val updatedAt: Instant,
    val curatedAt: Instant? = null,
    override val deletedAt: Instant? = null,
    val replies: List<ReviewReplyDetail>
) : CommentDetail {
    companion object
}

@Serializable
data class ReviewReplyDetail(
    override val id: Int,
    override val creator: UserDetail,
    override val text: String,
    override val createdAt: Instant,
    override val updatedAt: Instant,
    override val deletedAt: Instant? = null,
    var review: ReviewDetail? = null
) : CommentDetail

@Serializable data class ReviewsResponse(val docs: List<ReviewDetail>)

@Serializable data class PutReview(val text: String, val sentiment: ReviewSentiment, val captcha: String? = null)

@Serializable data class CurateReview(val id: Int, val curated: Boolean = false)

@Serializable data class DeleteReview(val reason: String)

@Serializable data class ReplyRequest(val text: String, val captcha: String? = null)

@Serializable data class RepliesResponse(val docs: List<ReviewReplyDetail>)
