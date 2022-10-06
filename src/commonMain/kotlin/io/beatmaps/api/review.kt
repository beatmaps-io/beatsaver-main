package io.beatmaps.api

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

class ReviewConstants {
    companion object {
        const val COMMENTS_ENABLED = false
    }
}

@Serializable
data class ReviewDetail(
    val id: Int,
    val creator: UserDetail,
    val text: String,
    val sentiment: ReviewSentiment,
    val createdAt: Instant,
    val curatedAt: Instant? = null,
    val deletedAt: Instant? = null
) {
    companion object
}

enum class ReviewSentiment {
    POSITIVE, NEGATIVE, NEUTRAL;

    companion object {
        fun fromInt(x: Int) =
            when (x) {
                1 -> POSITIVE
                -1 -> NEGATIVE
                else -> NEUTRAL
            }
    }
}
