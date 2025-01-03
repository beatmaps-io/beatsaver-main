package io.beatmaps.api

import io.beatmaps.api.user.from
import io.beatmaps.common.api.EAlertType
import io.beatmaps.common.api.EIssueType
import io.beatmaps.common.api.IDbIssueData
import io.beatmaps.common.api.MapReportData
import io.beatmaps.common.api.PlaylistReportData
import io.beatmaps.common.api.ReviewReportData
import io.beatmaps.common.api.UserReportData
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.db.updateReturning
import io.beatmaps.common.dbo.Alert
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Issue
import io.beatmaps.common.dbo.IssueComment
import io.beatmaps.common.dbo.IssueCommentDao
import io.beatmaps.common.dbo.IssueDao
import io.beatmaps.common.dbo.Playlist
import io.beatmaps.common.dbo.Review
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.UserDao
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.common.dbo.curatorAlias
import io.beatmaps.common.dbo.handleCurator
import io.beatmaps.common.dbo.handleUser
import io.beatmaps.common.dbo.joinCurator
import io.beatmaps.common.dbo.joinPlaylistCurator
import io.beatmaps.common.dbo.joinUploader
import io.beatmaps.common.dbo.joinUser
import io.beatmaps.common.dbo.joinVersions
import io.beatmaps.common.dbo.reviewerAlias
import io.beatmaps.common.pub
import io.beatmaps.util.cdnPrefix
import io.beatmaps.util.optionalAuthorization
import io.beatmaps.util.requireAuthorization
import io.beatmaps.util.requireCaptcha
import io.beatmaps.util.updateAlertCount
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.locations.Location
import io.ktor.server.locations.get
import io.ktor.server.locations.post
import io.ktor.server.locations.put
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.sql.Coalesce
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

@Location("/api/issues")
class IssueApi {
    @Location("/list/{page?}")
    data class IssueList(
        val open: Boolean? = null,
        val type: EIssueType? = null,
        val page: Long = 0,
        val api: IssueApi
    )

    @Location("/{id}")
    data class IssueDetail(val id: Int, val api: IssueApi)

    @Location("/comments/{id}/{commentId?}")
    data class IssueComments(val id: Int, val commentId: Int? = null, val api: IssueApi)

    @Location("/create")
    data class Issue(val api: IssueApi)
}

fun IDbIssueData.hydrate(cdnPrefix: String, isAdmin: Boolean): IHydratedIssueData? = when (this) {
    is MapReportData ->
        Beatmap
            .joinVersions()
            .joinUploader()
            .joinCurator()
            .selectAll()
            .where {
                (Beatmap.id eq id()).let { q ->
                    if (isAdmin) {
                        q
                    } else {
                        q and Beatmap.deletedAt.isNull()
                    }
                }
            }
            .complexToBeatmap()
            .singleOrNull()
            ?.let { HydratedMapReportData(MapDetail.from(it, cdnPrefix)) }
    is UserReportData -> HydratedUserReportData(UserDetail.from(UserDao[userId]))
    is PlaylistReportData ->
        Playlist
            .joinMaps()
            .joinUser(Playlist.owner)
            .joinPlaylistCurator()
            .select(Playlist.columns + User.columns + curatorAlias.columns + Playlist.Stats.all)
            .where {
                (Playlist.id eq playlistId).let { q ->
                    if (isAdmin) {
                        q
                    } else {
                        q and Playlist.deletedAt.isNull()
                    }
                }
            }
            .groupBy(Playlist.id, User.id, curatorAlias[User.id])
            .handleUser()
            .handleCurator()
            .singleOrNull()
            ?.let { HydratedPlaylistReportData(PlaylistFull.from(it, cdnPrefix)) }
    is ReviewReportData ->
        Review
            .join(reviewerAlias, JoinType.INNER, Review.userId, reviewerAlias[User.id])
            .join(Beatmap, JoinType.INNER, Review.mapId, Beatmap.id)
            .joinVersions()
            .joinUploader()
            .joinCurator()
            .selectAll()
            .where {
                (Review.id eq reviewId).let { q ->
                    if (isAdmin) {
                        q
                    } else {
                        q and Review.deletedAt.isNull() and Beatmap.deletedAt.isNull()
                    }
                }
            }
            .complexToReview()
            .singleOrNull()
            ?.let { HydratedReviewReportData(ReviewDetail.from(it, cdnPrefix)) }
}

fun IssueDetail.Companion.from(row: ResultRow, cdnPrefix: String, isAdmin: Boolean, comments: Iterable<ResultRow>? = null) = from(IssueDao.wrapRow(row), cdnPrefix, isAdmin, comments)

fun IssueDetail.Companion.from(other: IssueDao, cdnPrefix: String, isAdmin: Boolean, comments: Iterable<ResultRow>? = null) = IssueDetail(
    other.id.value,
    other.type,
    UserDetail.from(other.creator),
    other.createdAt.toKotlinInstant(),
    other.closedAt?.toKotlinInstant(),
    other.data.hydrate(cdnPrefix, isAdmin),
    comments?.map { IssueCommentDetail.from(it) }
)

fun IssueCommentDetail.Companion.from(row: ResultRow) = from(IssueCommentDao.wrapRow(row))

fun IssueCommentDetail.Companion.from(other: IssueCommentDao) = IssueCommentDetail(
    other.id.value,
    UserDetail.from(other.user),
    other.public,
    other.text,
    other.createdAt.toKotlinInstant(),
    other.updatedAt.toKotlinInstant()
)

fun Route.issueRoute(client: HttpClient) {
    post<IssueApi.Issue> {
        requireAuthorization { _, sess ->
            if (sess.suspended) {
                // User is suspended
                throw UserApiException("Suspended account")
            }

            val req = call.receive<IssueCreationRequest>()

            requireCaptcha(
                client,
                req.captcha,
                {
                    transaction {
                        Issue.insertAndGetId {
                            it[creator] = sess.userId
                            it[createdAt] = NowExpression(createdAt)
                            it[updatedAt] = NowExpression(updatedAt)
                            it[type] = req.data.typeEnum
                            it[data] = req.data
                        }.also { newId ->
                            IssueComment.insert {
                                it[issueId] = newId
                                it[text] = req.text.take(IssueConstants.MAX_COMMENT_LENGTH)
                                it[userId] = sess.userId
                                it[public] = true

                                it[createdAt] = NowExpression(createdAt)
                                it[updatedAt] = NowExpression(updatedAt)
                            }
                        }.value.also {
                            Alert.insert(
                                "You created an issue",
                                "You created a ${req.data.typeEnum.name} issue {$it}",
                                EAlertType.Issue,
                                sess.userId
                            )
                            updateAlertCount(sess.userId)
                        }
                    }
                }
            ) {
                throw ServerApiException("Could not verify user [${it.errorCodes.joinToString(", ")}]")
            }.let {
                call.pub("beatmaps", "issues.$it.created", null, it)
                call.respond(HttpStatusCode.Created, it)
            }
        }
    }

    get<IssueApi.IssueDetail> {
        optionalAuthorization { _, sess ->
            val issue = transaction {
                val issue = Issue
                    .joinUser(Issue.creator)
                    .selectAll()
                    .where {
                        (Issue.id eq it.id).let { q ->
                            if (sess?.isAdmin() == true) {
                                q
                            } else {
                                q and (Issue.creator eq sess?.userId)
                            }
                        }
                    }
                    .handleUser()
                    .singleOrNull() ?: throw NotFoundException()

                val comments = IssueComment
                    .joinUser(IssueComment.userId)
                    .selectAll()
                    .where { (IssueComment.issueId eq it.id) and IssueComment.deletedAt.isNull() and (if (sess?.isAdmin() == true) Op.TRUE else IssueComment.public) }
                    .orderBy(IssueComment.createdAt, SortOrder.ASC)
                    .handleUser()

                IssueDetail.from(issue, cdnPrefix(), sess?.isAdmin() == true, comments)
            }

            call.respond(issue)
        }
    }

    post<IssueApi.IssueDetail> { req ->
        requireAuthorization { _, sess ->
            if (!sess.isAdmin()) return@requireAuthorization ActionResponse.error("Unauthorised")

            val issueUpdate = call.receive<IssueUpdateRequest>()
            val success = transaction {
                Issue.update({ Issue.id eq req.id }) {
                    if (issueUpdate.closed) {
                        it[closedAt] = Coalesce(closedAt, NowExpression(closedAt))
                    } else {
                        it[closedAt] = null
                    }
                    it[updatedAt] = NowExpression(updatedAt)
                } > 0
            }

            if (success) {
                call.respond(ActionResponse.success())
            } else {
                call.respond(HttpStatusCode.NotFound, ActionResponse.error())
            }
        }
    }

    put<IssueApi.IssueComments> { req ->
        requireAuthorization { _, sess ->
            if (sess.suspended) {
                // User is suspended
                throw UserApiException("Suspended account")
            }

            val comment = call.receive<IssueCommentRequest>()

            val response = newSuspendedTransaction {
                val intermediaryResult = Issue
                    .updateReturning(
                        { Issue.id eq req.id and Issue.closedAt.isNull() },
                        {
                            it[updatedAt] = NowExpression(updatedAt)
                        },
                        Issue.id, Issue.creator
                    )
                    ?.singleOrNull()
                    ?.let { IssueDao.wrapRow(it) }

                if (intermediaryResult == null) {
                    rollback()
                    return@newSuspendedTransaction ActionResponse.error("Issue not found")
                }

                if (req.commentId == null) {
                    requireCaptcha(
                        client,
                        comment.captcha,
                        {
                            if (!sess.isAdmin() && sess.userId != intermediaryResult.creatorId.value) {
                                rollback()
                                return@requireCaptcha ActionResponse.error("Unauthorised")
                            }

                            IssueComment
                                .insertAndGetId {
                                    it[issueId] = req.id
                                    it[userId] = sess.userId
                                    it[text] = comment.text?.take(IssueConstants.MAX_COMMENT_LENGTH) ?: ""
                                    it[public] = if (!sess.isAdmin()) { true } else { comment.public ?: false }
                                    it[createdAt] = NowExpression(createdAt)
                                    it[updatedAt] = NowExpression(updatedAt)
                                }

                            ActionResponse.success()
                        }
                    )
                } else {
                    val success = IssueComment
                        .update({
                            ((IssueComment.id eq req.commentId) and (IssueComment.issueId eq req.id)).let { q ->
                                if (sess.isAdmin()) q else q.and(IssueComment.userId eq sess.userId)
                            }
                        }) {
                            if (comment.text != null) it[text] = comment.text.take(IssueConstants.MAX_COMMENT_LENGTH)
                            if (sess.isAdmin() && comment.public != null) it[public] = comment.public
                        } > 0

                    if (success) ActionResponse.success() else ActionResponse.error()
                }
            }

            call.respond(if (response.success) HttpStatusCode.OK else HttpStatusCode.NotFound, response)
        }
    }

    get<IssueApi.IssueList> { req ->
        requireAuthorization { _, sess ->
            if (!sess.isAdmin()) {
                call.respond(HttpStatusCode.Unauthorized)
                return@requireAuthorization
            }

            val ans = transaction {
                Issue
                    .joinUser(Issue.creator)
                    .selectAll()
                    .where {
                        Op.TRUE
                            .notNull(req.type) { o -> Issue.type eq o }
                            .notNull(req.open) { o -> Issue.closedAt.run { if (o) isNull() else isNotNull() } }
                    }
                    .orderBy(Issue.updatedAt, SortOrder.DESC)
                    .limit(req.page)
                    .handleUser()
                    .map {
                        IssueDetail.from(it, cdnPrefix(), sess.isAdmin())
                    }
            }
            call.respond(ans)
        }
    }
}
