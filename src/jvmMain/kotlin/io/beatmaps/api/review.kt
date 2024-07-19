package io.beatmaps.api

import io.beatmaps.common.ReplyDeleteData
import io.beatmaps.common.ReplyModerationData
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
import io.beatmaps.common.dbo.Collaboration
import io.beatmaps.common.dbo.ModLog
import io.beatmaps.common.dbo.Review
import io.beatmaps.common.dbo.ReviewDao
import io.beatmaps.common.dbo.ReviewReply
import io.beatmaps.common.dbo.ReviewReplyDao
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.UserDao
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.dbo.VersionsDao
import io.beatmaps.common.dbo.collaboratorAlias
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
import io.beatmaps.util.requireCaptcha
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
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.ColumnSet
import org.jetbrains.exposed.sql.Index
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.lang.Integer.toHexString

fun ReviewDetail.Companion.from(other: ReviewDao, cdnPrefix: String, beatmap: Boolean = true, user: Boolean = true) =
    ReviewDetail(
        other.id.value,
        if (user) UserDetail.from(other.user) else null,
        if (beatmap) MapDetail.from(other.map, cdnPrefix) else null,
        other.text,
        ReviewSentiment.fromInt(other.sentiment),
        other.createdAt.toKotlinInstant(), other.updatedAt.toKotlinInstant(), other.curatedAt?.toKotlinInstant(), other.deletedAt?.toKotlinInstant(),
        other.replies.values.map { ReviewReplyDetail.from(it) }
    )

fun ReviewReplyDetail.Companion.from(other: ReviewReplyDao) =
    ReviewReplyDetail(
        other.id.value,
        UserDetail.from(other.user),
        if (other.deletedAt == null) other.text else "",
        other.createdAt.toKotlinInstant(),
        other.updatedAt.toKotlinInstant(),
        other.deletedAt?.toKotlinInstant()
    )

fun ColumnSet.joinReplies() = join(ReviewReply, JoinType.LEFT, Review.id, ReviewReply.reviewId)

@Location("/api/review")
class ReviewApi {
    @Location("/id/{reviewId}")
    data class Detail(val reviewId: Int, val api: ReviewApi)

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

@Location("/api/reply")
class ReplyApi {
    @Location("/create/{reviewId}")
    data class Create(val reviewId: Int, val api: ReplyApi)

    @Location("/single/{replyId}")
    data class Single(val replyId: Int, val api: ReplyApi)

    @Location("/latest/{page}")
    data class ByDate(val before: Instant? = null, val user: String? = null, val page: Long = 0, val api: ReplyApi)
}

@Serializable
data class ReviewUpdateInfo(val mapId: Int, val userId: Int)

fun Query.complexToReview() = this.fold(mutableMapOf<EntityID<Int>, ReviewDao>()) { map, row ->
    map.also {
        it.getOrPut(row[Review.id]) {
            if (row.hasValue(reviewerAlias[User.id])) UserDao.wrapRow(row, reviewerAlias)
            if (row.hasValue(User.id)) UserDao.wrapRow(row)
            if (row.hasValue(curatorAlias[User.id]) && row[Beatmap.curator] != null) UserDao.wrapRow(row, curatorAlias)
            if (row.hasValue(Beatmap.id)) BeatmapDao.wrapRow(row)

            ReviewDao.wrapRow(row)
        }.run {
            if (row.hasValue(ReviewReply.id) && row.getOrNull(ReviewReply.id) != null) {
                replies.putIfAbsent(row[ReviewReply.id], ReviewReplyDao.wrapRow(row))
            }

            if (row.hasValue(Versions.id)) {
                this.map.versions.putIfAbsent(row[Versions.id], VersionsDao.wrapRow(row))
            }
        }
    }
}.values.toList()

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
                    .selectAll()
                    .where {
                        Review.deletedAt.isNull() and Beatmap.deletedAt.isNull()
                            .notNull(it.before) { o -> Review.createdAt less o.toJavaInstant() }
                            .notNull(it.user) { u -> reviewerAlias[User.uniqueName] eq u }
                    }
                    .orderBy(
                        Review.createdAt to SortOrder.DESC
                    )
                    .limit(it.page)
                    .complexToReview()
                    .map { ReviewDetail.from(it, cdnPrefix()) }
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

    get<ReviewApi.Detail> {
        val review = transaction {
            try {
                Review
                    .joinReplies()
                    .join(Beatmap, JoinType.INNER, Review.mapId, Beatmap.id)
                    .joinVersions(false)
                    .select(Review.columns + ReviewReply.columns)
                    .where {
                        Review.id eq it.reviewId and Review.deletedAt.isNull() and Beatmap.deletedAt.isNull()
                    }
                    .orderBy(
                        ReviewReply.createdAt to SortOrder.ASC
                    )
                    .complexToReview()
                    .singleOrNull()
                    ?.let { ReviewDetail.from(it, cdnPrefix()) }
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

    get<ReviewApi.ByMap> {
        val reviews = transaction {
            try {
                Review
                    .joinReplies()
                    .join(Beatmap, JoinType.INNER, Review.mapId, Beatmap.id)
                    .joinVersions(false)
                    .join(reviewerAlias, JoinType.INNER, Review.userId, reviewerAlias[User.id])
                    .select(Review.columns + ReviewReply.columns + reviewerAlias.columns)
                    .where {
                        Review.mapId eq it.id.toInt(16) and Review.deletedAt.isNull() and Beatmap.deletedAt.isNull()
                    }
                    .orderBy(
                        Review.curatedAt to SortOrder.DESC_NULLS_LAST,
                        Review.createdAt to SortOrder.DESC,
                        ReviewReply.createdAt to SortOrder.ASC
                    )
                    .limit(it.page)
                    .complexToReview()
                    .map {
                        ReviewDetail.from(it, cdnPrefix(), beatmap = false)
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
                    .joinReplies()
                    .join(Beatmap, JoinType.INNER, Review.mapId, Beatmap.id)
                    .joinVersions(false)
                    .joinUploader()
                    .joinCurator()
                    .selectAll()
                    .where {
                        Review.userId eq it.id and Review.deletedAt.isNull() and Beatmap.deletedAt.isNull()
                    }
                    .orderBy(
                        Review.createdAt to SortOrder.DESC,
                        ReviewReply.createdAt to SortOrder.ASC
                    )
                    .limit(it.page)
                    .complexToReview()
                    .map {
                        ReviewDetail.from(it, cdnPrefix(), user = false)
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
                    .joinReplies()
                    .join(Beatmap, JoinType.INNER, Review.mapId, Beatmap.id)
                    .joinVersions(false)
                    .select(Review.columns + ReviewReply.columns)
                    .where {
                        Review.mapId eq it.mapId.toInt(16) and (Review.userId eq it.userId) and Review.deletedAt.isNull() and Beatmap.deletedAt.isNull()
                    }
                    .orderBy(
                        ReviewReply.createdAt to SortOrder.ASC
                    )
                    .complexToReview()
                    .singleOrNull()
                    ?.let { ReviewDetail.from(it, cdnPrefix()) }
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
                        ReviewDao.wrapRow(Review.selectAll().where { Review.mapId eq updateMapId and (Review.userId eq single.userId) and Review.deletedAt.isNull() }.single())
                    } else {
                        null
                    }

                    if (update.captcha == null) {
                        Review.update({ Review.mapId eq updateMapId and (Review.userId eq single.userId) and Review.deletedAt.isNull() }) { r ->
                            r[updatedAt] = NowExpression(updatedAt)
                            r[text] = newText
                            r[sentiment] = update.sentiment.dbValue
                        }
                    } else {
                        val map = Beatmap.joinUploader().joinCollaborators().selectAll().where {
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
                            r[createdAt] = NowExpression(createdAt)
                            r[updatedAt] = NowExpression(updatedAt)
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

                        for (singleCollaborator in map.collaborators.values) {
                            if (!singleCollaborator.reviewAlerts) continue

                            Alert.insert(
                                "New review on a map you collaborated on",
                                "@${sess.uniqueName} just reviewed a map you collaborated on #${toHexString(updateMapId)}: **${map.name}**.\n" +
                                    "*\"${newText.replace(Regex("\n+"), " ").take(100)}...\"*",
                                EAlertType.Review,
                                singleCollaborator.id.value
                            )
                            updateAlertCount(singleCollaborator.id.value)
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
                    r[deletedAt] = NowExpression(deletedAt)
                }, Review.id, Review.text, Review.sentiment)

                if (!result.isNullOrEmpty()) {
                    ReviewReply.deleteWhere { reviewId inList result.map { it[Review.id] } }

                    if (single.userId != sess.userId) {
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
                                it[curatedAt] = NowExpression(curatedAt)
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

    post<ReplyApi.Create> { req ->
        requireAuthorization { _, user ->
            if (user.suspended) return@requireAuthorization call.respond(ActionResponse(false, listOf("Suspended account")))

            val reply = call.receive<ReplyRequest>()

            if (reply.captcha == null) return@requireAuthorization call.respond(ActionResponse(false, listOf("Missing Captcha")))

            val response = requireCaptcha(
                reply.captcha,
                {
                    val (insertedId, response) = newSuspendedTransaction {
                        val intermediaryResult = Review
                            .join(Beatmap, JoinType.LEFT, Review.mapId, Beatmap.id)
                            .select(Review.userId, Beatmap.id, Beatmap.name, Beatmap.uploader)
                            .where { Review.id eq req.reviewId and Beatmap.deletedAt.isNull() and Review.deletedAt.isNull() }
                            .firstOrNull()

                        if (intermediaryResult == null) {
                            return@newSuspendedTransaction Pair(
                                null,
                                ActionResponse(false, listOf("Review or map not found"))
                            )
                        }

                        val reviewUserId = intermediaryResult[Review.userId].value
                        val mapId = intermediaryResult[Beatmap.id].value
                        val mapName = intermediaryResult[Beatmap.name]
                        val uploadUserId = intermediaryResult[Beatmap.uploader].value

                        val collaborators = Collaboration
                            .join(User, JoinType.LEFT, Collaboration.collaboratorId, User.id)
                            .select(Collaboration.collaboratorId, User.reviewAlerts)
                            .where { Collaboration.mapId eq mapId and Collaboration.accepted }

                        val mapperIds = listOf(
                            uploadUserId,
                            *collaborators.map { it[Collaboration.collaboratorId].value }.toTypedArray()
                        )

                        val allowedUsers = listOf(
                            *mapperIds.toTypedArray(),
                            reviewUserId
                        )

                        if (user.userId !in allowedUsers) {
                            return@newSuspendedTransaction Pair(null, ActionResponse(false, listOf("Unauthorised")))
                        }

                        val insertedId = ReviewReply.insertAndGetId {
                            it[userId] = user.userId
                            it[reviewId] = req.reviewId
                            it[text] = reply.text
                            it[createdAt] = NowExpression(createdAt)
                            it[updatedAt] = NowExpression(updatedAt)
                        }.value

                        if (insertedId != null) {
                            val alertHeader = "New Review Reply"

                            if (user.userId != reviewUserId) {
                                Alert.insert(
                                    alertHeader,
                                    "@${user.uniqueName} just replied to your review on #${toHexString(mapId)}: **$mapName**.\n" +
                                        "*\"${reply.text.replace(Regex("\n+"), " ").take(100)}...\"*",
                                    EAlertType.ReviewReply,
                                    reviewUserId
                                )

                                updateAlertCount(reviewUserId)
                            }

                            for (singleCollaborator in collaborators) {
                                if (singleCollaborator[Collaboration.collaboratorId].value == user.userId) continue
                                if (!singleCollaborator[User.reviewAlerts]) continue

                                Alert.insert(
                                    alertHeader,
                                    "@${user.uniqueName} just replied to a review on #${toHexString(mapId)}: **$mapName**.\n" +
                                        "*\"${reply.text.replace(Regex("\n+"), " ").take(100)}...\"*",
                                    EAlertType.ReviewReply,
                                    singleCollaborator[Collaboration.collaboratorId].value
                                )

                                updateAlertCount(singleCollaborator[Collaboration.collaboratorId].value)
                            }
                        }

                        Pair(insertedId, ActionResponse(true, listOf()))
                    }

                    if (insertedId != null) {
                        call.pub("beatmaps", "ws.review-replies.created", null, insertedId)
                    }

                    response
                }
            ) { e ->
                ActionResponse(false, e.errorCodes.map { "Captcha error: $it" })
            }

            call.respond(response)
        }
    }

    put<ReplyApi.Single> { req ->
        requireAuthorization { _, user ->
            val update = call.receive<ReplyRequest>()

            captchaIfPresent(update.captcha) {
                val response = newSuspendedTransaction {
                    val ownerId = ReviewReply
                        .select(ReviewReply.userId)
                        .where { ReviewReply.id eq req.replyId }
                        .single().let { it[ReviewReply.userId].value }

                    if (ownerId != user.userId && !user.isCurator()) {
                        return@newSuspendedTransaction ActionResponse(false, listOf("Unauthorised"))
                    }

                    val oldData = if (ownerId != user.userId) {
                        ReviewReplyDao.wrapRow(ReviewReply.selectAll().where { ReviewReply.id eq req.replyId and ReviewReply.deletedAt.isNull() }.single())
                    } else {
                        null
                    }

                    val updated = ReviewReply.update({ ReviewReply.id eq req.replyId and ReviewReply.deletedAt.isNull() }) {
                        it[text] = update.text
                        it[updatedAt] = NowExpression(updatedAt)
                    } > 0

                    if (updated && ownerId != user.userId && oldData != null) {
                        val (mapId, userId) = ReviewReply
                            .join(Review, JoinType.INNER, ReviewReply.reviewId, Review.id)
                            .select(Review.mapId, ReviewReply.userId)
                            .where { ReviewReply.id eq req.replyId }
                            .single().let { it[Review.mapId].value to it[ReviewReply.userId].value }

                        ModLog.insert(
                            user.userId,
                            mapId,
                            ReplyModerationData(oldData.text, update.text),
                            userId
                        )
                    }

                    ActionResponse(updated, listOf())
                }

                // This should be outside the transaction - otherwise the websocket will send the old text
                if (response.success) {
                    call.pub("beatmaps", "ws.review-replies.updated", null, req.replyId)
                }

                call.respond(response)
            }
        }
    }

    delete<ReplyApi.Single> { req ->
        val delete = call.receive<DeleteReview>()

        requireAuthorization { _, user ->
            val response = newSuspendedTransaction {
                val ownerId = ReviewReply
                    .select(ReviewReply.userId)
                    .where { ReviewReply.id eq req.replyId }
                    .single().let { it[ReviewReply.userId].value }

                if (ownerId != user.userId && !user.isCurator()) {
                    return@newSuspendedTransaction HttpStatusCode.Unauthorized
                }

                val deleted = ReviewReply
                    .updateReturning({
                        ReviewReply.id eq req.replyId and ReviewReply.deletedAt.isNull()
                    }, {
                        it[deletedAt] = NowExpression(deletedAt)
                    }, *ReviewReply.columns.toTypedArray())?.firstOrNull()

                deleted?.let { d ->
                    if (ownerId != user.userId) {
                        val mapId = Review
                            .select(Review.mapId)
                            .where { Review.id eq d[ReviewReply.reviewId] }
                            .single().let { it[Review.mapId].value }

                        ModLog.insert(
                            user.userId,
                            mapId,
                            ReplyDeleteData(delete.reason, d[ReviewReply.text]),
                            ownerId
                        )

                        Alert.insert(
                            "Your reply was deleted",
                            "A moderator deleted your reply on #${toHexString(mapId)}.\n" +
                                "Reason: *\"${delete.reason}\"*",
                            EAlertType.ReviewDeletion,
                            d[ReviewReply.userId].value
                        )
                    }
                }

                // This can be inside the delete transaction since only the ID is needed and no data is retrieved
                if (deleted != null) {
                    call.pub("beatmaps", "ws.review-replies.deleted", null, deleted[ReviewReply.id].value)
                    HttpStatusCode.OK
                } else {
                    HttpStatusCode.NotModified
                }
            }

            call.respond(response)
        }
    }

    get<ReplyApi.ByDate> {
        val replies = transaction {
            try {
                ReviewReply
                    .join(reviewerAlias, JoinType.INNER, ReviewReply.userId, reviewerAlias[User.id])
                    .join(Review, JoinType.INNER, ReviewReply.reviewId, Review.id)
                    .join(Beatmap, JoinType.INNER, Review.mapId, Beatmap.id)
                    .selectAll()
                    .where {
                        ReviewReply.deletedAt.isNull() and Beatmap.deletedAt.isNull()
                            .notNull(it.before) { o -> ReviewReply.createdAt less o.toJavaInstant() }
                            .notNull(it.user) { u -> reviewerAlias[User.uniqueName] eq u }
                    }
                    .orderBy(
                        ReviewReply.createdAt to SortOrder.DESC
                    )
                    .limit(it.page)
                    .complexToReview()
                    .map { r -> ReviewDetail.from(r, cdnPrefix()) }
                    .flatMap { review ->
                        review.replies.map { reply ->
                            reply.apply {
                                this.review = review.copy(replies = listOf())
                            }
                        }
                    }
            } catch (_: NumberFormatException) {
                null
            }
        }

        if (replies == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respond(RepliesResponse(replies))
        }
    }
}
