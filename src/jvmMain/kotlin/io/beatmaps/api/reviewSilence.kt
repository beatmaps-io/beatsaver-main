package io.beatmaps.api

import io.beatmaps.common.dbo.ReviewSilence
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or

private fun activeFor(userId: Int, now: Instant = Clock.System.now()) =
    (ReviewSilence.userId eq userId) and
        ReviewSilence.revokedAt.isNull() and
        (ReviewSilence.silencedUntil.isNull() or (ReviewSilence.silencedUntil greater now.toJavaInstant()))

fun reviewSilenced(userId: Int) =
    ReviewSilence
        .select(ReviewSilence.id)
        .where {
            activeFor(userId)
        }
        .limit(1)
        .firstOrNull() != null

fun requireNotReviewSilenced(userId: Int) {
    if (reviewSilenced(userId)) {
        throw UserApiException("You are currently silenced and cannot review maps or reply to reviews.")
    }
}
