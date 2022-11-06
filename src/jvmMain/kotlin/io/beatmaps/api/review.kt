package io.beatmaps.api

import io.beatmaps.cdnPrefix
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.db.upsert
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.BeatmapDao
import io.beatmaps.common.dbo.Review
import io.beatmaps.common.dbo.ReviewDao
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.UserDao
import io.beatmaps.common.dbo.curatorAlias
import io.beatmaps.common.dbo.joinCurator
import io.beatmaps.common.dbo.joinUploader
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.locations.Location
import io.ktor.server.locations.get
import io.ktor.server.locations.post
import io.ktor.server.locations.put
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.sql.Index
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

fun ReviewDetail.Companion.from(other: ReviewDao, cdnPrefix: String, beatmap: Boolean) =
    ReviewDetail(
        other.id.value,
        if (beatmap) null else UserDetail.from(other.user),
        if (beatmap) MapDetail.from(other.map, cdnPrefix) else null,
        other.text,
        ReviewSentiment.fromInt(other.sentiment),
        other.createdAt.toKotlinInstant(), other.updatedAt.toKotlinInstant(), other.curatedAt?.toKotlinInstant(), other.deletedAt?.toKotlinInstant()
    )

fun ReviewDetail.Companion.from(row: ResultRow, cdnPrefix: String) = from(ReviewDao.wrapRow(row), cdnPrefix, row.hasValue(Beatmap.id))

@Location("/api/review")
class ReviewApi {
    @Location("/map/{id}/{page?}")
    data class ByMap(val id: String, val page: Long = 0, val api: ReviewApi)

    @Location("/user/{id}/{page?}")
    data class ByUser(val id: Int, val page: Long = 0, val api: ReviewApi)

    @Location("/single/{mapId}/{userId}")
    data class Single(val mapId: String, val userId: Int, val api: ReviewApi)

    @Location("/curate")
    data class Curate(val api: ReviewApi)
}

fun Route.reviewRoute() {
    if (!ReviewConstants.COMMENTS_ENABLED) return

    get<ReviewApi.ByMap> {
        val reviews = transaction {
            try {
                Review
                    .join(User, JoinType.INNER, Review.userId, User.id)
                    .select {
                        Review.mapId eq it.id.toInt(16) and Review.deletedAt.isNull()
                    }
                    .orderBy(
                        Review.curatedAt to SortOrder.DESC_NULLS_LAST,
                        Review.createdAt to SortOrder.DESC
                    )
                    .limit(it.page)
                    .map {
                        UserDao.wrapRow(it)
                        ReviewDetail.from(it, cdnPrefix())
                    }
            } catch (_: NumberFormatException) {
                null
            }
        }

        if (reviews == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respond(ReviewsResponse(reviews))
        }
    }

    get<ReviewApi.ByUser> {
        val reviews = transaction {
            try {
                Review
                    .join(Beatmap, JoinType.INNER, Review.mapId, Beatmap.id)
                    .joinUploader()
                    .joinCurator()
                    .select {
                        Review.userId eq it.id and Review.deletedAt.isNull()
                    }
                    .orderBy(
                        Review.createdAt, SortOrder.DESC
                    )
                    .limit(it.page)
                    .map { row ->
                        if (row.hasValue(User.id)) {
                            UserDao.wrapRow(row)
                        }
                        if (row.hasValue(curatorAlias[User.id]) && row[Beatmap.curator] != null) {
                            UserDao.wrapRow(row, curatorAlias)
                        }

                        BeatmapDao.wrapRow(row)
                        ReviewDetail.from(row, cdnPrefix())
                    }
            } catch (_: NumberFormatException) {
                null
            }
        }

        if (reviews == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respond(ReviewsResponse(reviews))
        }
    }

    get<ReviewApi.Single> {
        val review = transaction {
            try {
                Review
                    .join(User, JoinType.INNER, Review.userId, User.id)
                    .select {
                        Review.mapId eq it.mapId.toInt(16) and (Review.userId eq it.userId) and Review.deletedAt.isNull()
                    }
                    .singleOrNull()
                    ?.let { row ->
                        UserDao.wrapRow(row)
                        ReviewDetail.from(row, cdnPrefix())
                    }
            } catch (_: NumberFormatException) {
                null
            }
        }

        if (review == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respond(review)
        }
    }

    put<ReviewApi.Single> { single ->
        requireAuthorization { sess ->
            val update = call.receive<PutReview>()

            if (single.userId != sess.userId) {
                call.respond(HttpStatusCode.Forbidden)
                return@requireAuthorization
            }

            captchaIfPresent(update.captcha) {
                val reviewAtNew = Instant.now()

                transaction {
                    if (update.captcha == null) {
                        Review.update({ Review.mapId eq single.mapId.toInt(16) and (Review.userId eq sess.userId) and Review.deletedAt.isNull() }) { r ->
                            r[updatedAt] = reviewAtNew
                            r[text] = update.text.take(ReviewConstants.MAX_LENGTH)
                            r[sentiment] = update.sentiment.dbValue
                        }
                    } else {
                        Review.upsert(conflictIndex = Index(listOf(Review.mapId, Review.userId), true, "review_unique")) { r ->
                            r[mapId] = single.mapId.toInt(16)
                            r[userId] = sess.userId
                            r[text] = update.text.take(ReviewConstants.MAX_LENGTH)
                            r[sentiment] = update.sentiment.dbValue
                            r[createdAt] = reviewAtNew
                            r[updatedAt] = reviewAtNew
                        }
                    }
                }

                call.respond(HttpStatusCode.OK)
            }
        }
    }

    post<ReviewApi.Curate> {
        requireAuthorization { user ->
            if (!user.isCurator()) {
                call.respond(HttpStatusCode.BadRequest)
            } else {
                val reviewUpdate = call.receive<CurateReview>()

                transaction {
                    fun curateReview() =
                        Review.update({
                            (Review.id eq reviewUpdate.id) and (if (reviewUpdate.curated) Review.curatedAt.isNull() else Review.curatedAt.isNotNull())
                        }) {
                            if (reviewUpdate.curated) {
                                it[curatedAt] = NowExpression(curatedAt.columnType)
                            } else {
                                it[curatedAt] = null
                            }
                        }

                    curateReview()
                }

                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
