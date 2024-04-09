package io.beatmaps.api

import io.beatmaps.common.ReviewDeleteData
import io.beatmaps.common.ReviewModerationData
import io.beatmaps.common.api.EAlertType
import io.beatmaps.common.api.ReviewSentiment
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.db.updateReturning
import io.beatmaps.common.db.upsert
import io.beatmaps.common.dbo.Alert
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.BeatmapDao
import io.beatmaps.common.dbo.ModLog
import io.beatmaps.common.dbo.Review
import io.beatmaps.common.dbo.ReviewDao
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.UserDao
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.common.dbo.curatorAlias
import io.beatmaps.common.dbo.joinCollaborators
import io.beatmaps.common.dbo.joinCurator
import io.beatmaps.common.dbo.joinUploader
import io.beatmaps.common.dbo.joinVersions
import io.beatmaps.common.dbo.reviewerAlias
import io.beatmaps.common.pub
import io.beatmaps.util.captchaIfPresent
import io.beatmaps.util.cdnPrefix
import io.beatmaps.util.requireAuthorization
import io.beatmaps.util.updateAlertCount
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.locations.Location
import io.ktor.server.locations.delete
import io.ktor.server.locations.get
import io.ktor.server.locations.post
import io.ktor.server.locations.put
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.sql.Index
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.lang.Integer.toHexString

fun ReviewDetail.Companion.from(other: ReviewDao, cdnPrefix: String, beatmap: Boolean, user: Boolean) =
    ReviewDetail(
        other.id.value,
        if (user) UserDetail.from(other.user) else null,
        if (beatmap) MapDetail.from(other.map, cdnPrefix) else null,
        other.text,
        ReviewSentiment.fromInt(other.sentiment),
        other.createdAt.toKotlinInstant(), other.updatedAt.toKotlinInstant(), other.curatedAt?.toKotlinInstant(), other.deletedAt?.toKotlinInstant()
    )

fun ReviewDetail.Companion.from(row: ResultRow, cdnPrefix: String) = from(ReviewDao.wrapRow(row), cdnPrefix, row.hasValue(Beatmap.id), row.hasValue(reviewerAlias[User.id]))

@Location("/api/review")
class ReviewApi {
    @Location("/map/{id}/{page?}")
    data class ByMap(val id: String, val page: Long = 0, val api: ReviewApi)

    @Location("/user/{id}/{page?}")
    data class ByUser(val id: Int, val page: Long = 0, val api: ReviewApi)

    @Location("/single/{mapId}/{userId}")
    data class Single(val mapId: String, val userId: Int, val api: ReviewApi)

    @Location("/latest/{page}")
    data class ByDate(val before: Instant? = null, val user: String? = null, val page: Long = 0, val api: ReviewApi)

    @Location("/curate")
    data class Curate(val api: ReviewApi)
}

data class ReviewUpdateInfo(val mapId: Int, val userId: Int)

fun reviewToComplex(row: ResultRow, prefix: String): ReviewDetail {
    if (row.hasValue(reviewerAlias[User.id])) UserDao.wrapRow(row, reviewerAlias)
    if (row.hasValue(User.id)) UserDao.wrapRow(row)
    if (row.hasValue(curatorAlias[User.id]) && row[Beatmap.curator] != null) UserDao.wrapRow(row, curatorAlias)
    if (row.hasValue(Beatmap.id)) BeatmapDao.wrapRow(row)

    return ReviewDetail.from(row, prefix)
}

fun Route.reviewRoute() {
    if (!ReviewConstants.COMMENTS_ENABLED) return

    get<ReviewApi.ByDate> {
        val reviews = transaction {
            try {
                Review
                    .join(reviewerAlias, JoinType.INNER, Review.userId, reviewerAlias[User.id])
                    .join(Beatmap, JoinType.INNER, Review.mapId, Beatmap.id)
                    .joinVersions(false)
                    .joinUploader()
                    .joinCurator()
                    .select {
                        Review.deletedAt.isNull() and Beatmap.deletedAt.isNull()
                            .notNull(it.before) { o -> Review.createdAt less o.toJavaInstant() }
                            .notNull(it.user) { u -> reviewerAlias[User.uniqueName] eq u }
                    }
                    .orderBy(
                        Review.createdAt to SortOrder.DESC
                    )
                    .limit(it.page)
                    .map { row ->
                        reviewToComplex(row, cdnPrefix())
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

    get<ReviewApi.ByMap> {
        val reviews = transaction {
            try {
                Review
                    .join(Beatmap, JoinType.INNER, Review.mapId, Beatmap.id)
                    .joinVersions(false)
                    .join(reviewerAlias, JoinType.INNER, Review.userId, reviewerAlias[User.id])
                    .slice(Review.columns + reviewerAlias.columns)
                    .select {
                        Review.mapId eq it.id.toInt(16) and Review.deletedAt.isNull() and Beatmap.deletedAt.isNull()
                    }
                    .orderBy(
                        Review.curatedAt to SortOrder.DESC_NULLS_LAST,
                        Review.createdAt to SortOrder.DESC
                    )
                    .limit(it.page)
                    .map {
                        reviewToComplex(it, cdnPrefix())
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
                    .joinVersions(false)
                    .joinUploader()
                    .joinCurator()
                    .select {
                        Review.userId eq it.id and Review.deletedAt.isNull() and Beatmap.deletedAt.isNull()
                    }
                    .orderBy(
                        Review.createdAt, SortOrder.DESC
                    )
                    .limit(it.page)
                    .map { row ->
                        reviewToComplex(row, cdnPrefix())
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
                    .join(Beatmap, JoinType.INNER, Review.mapId, Beatmap.id)
                    .joinVersions(false)
                    .slice(Review.columns)
                    .select {
                        Review.mapId eq it.mapId.toInt(16) and (Review.userId eq it.userId) and Review.deletedAt.isNull() and Beatmap.deletedAt.isNull()
                    }
                    .singleOrNull()
                    ?.let { row ->
                        reviewToComplex(row, cdnPrefix())
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
        requireAuthorization { _, sess ->
            val update = call.receive<PutReview>()
            val updateMapId = single.mapId.toInt(16)
            val newText = update.text.take(ReviewConstants.MAX_LENGTH)

            captchaIfPresent(update.captcha) {
                val success = newSuspendedTransaction {
                    if (single.userId != sess.userId && !sess.isCurator()) {
                        call.respond(HttpStatusCode.Forbidden)
                        return@newSuspendedTransaction false
                    }

                    if (sess.suspended || UserDao[sess.userId].suspendedAt != null) {
                        // User is suspended
                        call.respond(ActionResponse(false, listOf("Suspended account")))
                        return@newSuspendedTransaction false
                    }

                    val oldData = if (single.userId != sess.userId) {
                        ReviewDao.wrapRow(Review.select { Review.mapId eq updateMapId and (Review.userId eq single.userId) and Review.deletedAt.isNull() }.single())
                    } else {
                        null
                    }

                    if (update.captcha == null) {
                        Review.update({ Review.mapId eq updateMapId and (Review.userId eq single.userId) and Review.deletedAt.isNull() }) { r ->
                            r[updatedAt] = NowExpression(updatedAt.columnType)
                            r[text] = newText
                            r[sentiment] = update.sentiment.dbValue
                        }
                    } else {
                        val map = Beatmap.joinUploader().joinCollaborators().select {
                            Beatmap.id eq updateMapId
                        }.complexToBeatmap().single()

                        if (map.uploaderId.value == single.userId) {
                            // Can't review your own map
                            call.respond(ActionResponse(false, listOf("Own map")))
                            return@newSuspendedTransaction false
                        }

                        val isCollaborator = map.collaborators.values.any { singleCollaborator ->
                            singleCollaborator.id.value == single.userId
                        }

                        if (isCollaborator) {
                            // Can't review maps that you collaborated on
                            call.respond(ActionResponse(false, listOf("You're a collaborator of this map")))
                            return@newSuspendedTransaction false
                        }

                        Review.upsert(conflictIndex = Index(listOf(Review.mapId, Review.userId), true, "review_unique")) { r ->
                            r[mapId] = updateMapId
                            r[userId] = single.userId
                            r[text] = newText
                            r[sentiment] = update.sentiment.dbValue
                            r[createdAt] = NowExpression(createdAt.columnType)
                            r[updatedAt] = NowExpression(updatedAt.columnType)
                            r[deletedAt] = null
                        }

                        if (map.uploader.reviewAlerts) {
                            Alert.insert(
                                "New review on your map",
                                "@${sess.uniqueName} just reviewed your map #${toHexString(updateMapId)}: **${map.name}**.\n" +
                                    "*\"${newText.replace(Regex("\n+"), " ").take(100)}...\"*",
                                EAlertType.Review,
                                map.uploaderId.value
                            )
                            updateAlertCount(map.uploaderId.value)
                        }
                    }

                    if (single.userId != sess.userId && oldData != null) {
                        ModLog.insert(
                            sess.userId,
                            updateMapId,
                            ReviewModerationData(oldData.sentiment, update.sentiment.dbValue, oldData.text, newText),
                            single.userId
                        )
                    }

                    true
                }

                if (success) {
                    val updateType = if (update.captcha == null) "updated" else "created"
                    call.pub("beatmaps", "reviews.$updateMapId.$updateType", null, ReviewUpdateInfo(updateMapId, single.userId))
                    call.respond(ActionResponse(true, listOf()))
                }
            }
        }
    }

    delete<ReviewApi.Single> { single ->
        requireAuthorization { _, sess ->
            val deleteReview = call.receive<DeleteReview>()
            val mapId = single.mapId.toInt(16)

            if (single.userId != sess.userId && !sess.isCurator()) {
                call.respond(HttpStatusCode.Forbidden)
                return@requireAuthorization
            }

            transaction {
                val result = Review.updateReturning({ Review.mapId eq mapId and (Review.userId eq single.userId) and Review.deletedAt.isNull() }, { r ->
                    r[deletedAt] = NowExpression(deletedAt.columnType)
                }, Review.text, Review.sentiment)

                if (!result.isNullOrEmpty() && single.userId != sess.userId) {
                    val info = result.first().let {
                        it[Review.text] to it[Review.sentiment]
                    }

                    ModLog.insert(
                        sess.userId,
                        mapId,
                        ReviewDeleteData(deleteReview.reason, info.first, info.second),
                        single.userId
                    )

                    Alert.insert(
                        "Your review was deleted",
                        "A moderator deleted your review on #${toHexString(mapId)}.\n" +
                            "Reason: *\"${deleteReview.reason}\"*",
                        EAlertType.ReviewDeletion,
                        single.userId
                    )
                    updateAlertCount(single.userId)
                }
            }

            call.pub("beatmaps", "reviews.$mapId.deleted", null, ReviewUpdateInfo(mapId, single.userId))
            call.respond(HttpStatusCode.OK)
        }
    }

    post<ReviewApi.Curate> {
        requireAuthorization { _, user ->
            if (!user.isCurator()) {
                call.respond(HttpStatusCode.BadRequest)
            } else {
                val reviewUpdate = call.receive<CurateReview>()

                transaction {
                    fun curateReview() =
                        Review.updateReturning({
                            (Review.id eq reviewUpdate.id) and (if (reviewUpdate.curated) Review.curatedAt.isNull() else Review.curatedAt.isNotNull()) and Review.deletedAt.isNull()
                        }, {
                            if (reviewUpdate.curated) {
                                it[curatedAt] = NowExpression(curatedAt.columnType)
                            } else {
                                it[curatedAt] = null
                            }
                        }, Review.mapId, Review.userId)
                            ?.singleOrNull()
                            ?.let { ReviewUpdateInfo(it[Review.mapId].value, it[Review.userId].value) }

                    curateReview()
                }?.let {
                    call.pub("beatmaps", "reviews.${it.mapId}.curated", null, it)
                }

                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
