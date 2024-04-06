package io.beatmaps.api

import io.beatmaps.common.api.ReviewSentiment
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

class ReviewConstants {
    companion object {
        const val COMMENTS_ENABLED = true
        const val MAX_LENGTH = 2000
        const val MINIMUM_REVIEWS = 5
    }
}

@Serializable
data class ReviewDetail(
    val id: Int,
    val creator: UserDetail? = null,
    val map: MapDetail? = null,
    val text: String,
    val sentiment: ReviewSentiment,
    val createdAt: Instant,
    val updatedAt: Instant,
    val curatedAt: Instant? = null,
    val deletedAt: Instant? = null
) {
    companion object
}

@Serializable data class ReviewsResponse(val docs: List<ReviewDetail>)

@Serializable data class PutReview(val text: String, val sentiment: ReviewSentiment, val captcha: String? = null)

@Serializable data class CurateReview(val id: Int, val curated: Boolean = false)

@Serializable data class DeleteReview(val reason: String)
