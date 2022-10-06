package io.beatmaps.api

import io.beatmaps.common.dbo.Review
import io.beatmaps.common.dbo.ReviewDao
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.UserDao
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.locations.Location
import io.ktor.server.locations.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

fun ReviewDetail.Companion.from(other: ReviewDao) =
    ReviewDetail(
        other.id.value, UserDetail.from(other.user), other.text, ReviewSentiment.fromInt(other.sentiment), other.createdAt.toKotlinInstant(), other.curatedAt?.toKotlinInstant(), other.deletedAt?.toKotlinInstant()
    )

fun ReviewDetail.Companion.from(row: ResultRow) = from(ReviewDao.wrapRow(row))

@Location("/api/review")
class ReviewApi {
    @Location("/map/{id}/{page?}")
    data class ByMap(val id: String, val page: Long = 0, val api: ReviewApi)

    @Location("/user/{id}/{page?}")
    data class ByUser(val id: Int, val page: Long = 0, val api: ReviewApi)
}

fun Route.reviewRoute() {
    if (!ReviewConstants.COMMENTS_ENABLED) return

    get<ReviewApi.ByMap> {
        val reviews = transaction {
            try {
                Review
                    .join(User, JoinType.INNER, Review.userId, User.id)
                    .select {
                        Review.mapId eq it.id.toInt(16)
                    }
                    .limit(it.page)
                    .map {
                        UserDao.wrapRow(it)
                        ReviewDetail.from(it)
                    }
            } catch (_: NumberFormatException) {
                null
            }
        }

        if (reviews == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respond(reviews)
        }
    }

    get<ReviewApi.ByUser> {
        val reviews = transaction {
            try {
                Review
                    .select {
                        Review.userId eq it.id
                    }
                    .limit(it.page)
                    .map {
                        ReviewDetail.from(it)
                    }
            } catch (_: NumberFormatException) {
                null
            }
        }

        if (reviews == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respond(reviews)
        }
    }
}
