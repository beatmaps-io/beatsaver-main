package io.beatmaps.api

import io.beatmaps.common.dbo.User
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.or

object ReviewSilence : IntIdTable("review_silence", "silenceId") {
    val userId = reference("userId", User)
    val moderatorId = reference("moderatorId", User)
    val createdAt = timestamp("createdAt")
    val silencedUntil = timestamp("silencedUntil").nullable()
    val durationMinutes = integer("durationMinutes").nullable()
    val reason = text("reason").nullable()
    val revokedAt = timestamp("revokedAt").nullable()

    private fun activeFor(userId: Int, now: java.time.Instant) =
        (ReviewSilence.userId eq userId) and revokedAt.isNull() and (silencedUntil.isNull() or (silencedUntil greater now))

    fun active(userId: Int) =
        select(id)
            .where {
                activeFor(userId, Clock.System.now().toJavaInstant())
            }
            .limit(1)
            .firstOrNull() != null

    fun activeUntil(userId: Int) =
        select(silencedUntil)
            .where {
                activeFor(userId, Clock.System.now().toJavaInstant())
            }
            .orderBy(silencedUntil, SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?.get(silencedUntil)
}

fun requireNotReviewSilenced(userId: Int) {
    if (ReviewSilence.active(userId)) {
        throw UserApiException("Silenced from reviewing maps")
    }
}
