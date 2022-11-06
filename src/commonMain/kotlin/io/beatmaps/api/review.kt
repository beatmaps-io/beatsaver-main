package io.beatmaps.api

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

class ReviewConstants {
    companion object {
        const val COMMENTS_ENABLED = false
        const val MAX_LENGTH = 2000
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

@Serializable
data class ReviewsResponse(val docs: List<ReviewDetail>)

@Serializable data class PutReview(val text: String, val sentiment: ReviewSentiment, val captcha: String? = null)
@Serializable data class CurateReview(val id: Int, val curated: Boolean = false)
@Serializable data class DeleteReview(val reason: String)

enum class ReviewSentiment(val dbValue: Int) {
    POSITIVE(1), NEGATIVE(-1), NEUTRAL(0);

    companion object {
        fun fromInt(x: Int) =
            when (x) {
                POSITIVE.dbValue -> POSITIVE
                NEGATIVE.dbValue -> NEGATIVE
                else -> NEUTRAL
            }
    }
}
