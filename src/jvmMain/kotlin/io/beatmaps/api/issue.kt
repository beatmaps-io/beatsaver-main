package io.beatmaps.api

import io.beatmaps.api.user.from
import io.beatmaps.api.user.getAvatar
import io.beatmaps.common.amqp.pub
import io.beatmaps.common.api.BasicMapInfo
import io.beatmaps.common.api.BasicPlaylistInfo
import io.beatmaps.common.api.BasicReviewInfo
import io.beatmaps.common.api.BasicUserInfo
import io.beatmaps.common.api.DbMapReportData
import io.beatmaps.common.api.DbPlaylistReportData
import io.beatmaps.common.api.DbReviewReportData
import io.beatmaps.common.api.DbUserReportData
import io.beatmaps.common.api.EAlertType
import io.beatmaps.common.api.EIssueType
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.api.EPlaylistType
import io.beatmaps.common.api.IDbIssueData
import io.beatmaps.common.api.MapReportDataBase
import io.beatmaps.common.api.PlaylistReportDataBase
import io.beatmaps.common.api.ReviewReportDataBase
import io.beatmaps.common.api.ReviewSentiment
import io.beatmaps.common.api.UserReportDataBase
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.db.updateReturning
import io.beatmaps.common.dbo.Alert
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.BeatmapDao
import io.beatmaps.common.dbo.Issue
import io.beatmaps.common.dbo.IssueComment
import io.beatmaps.common.dbo.IssueCommentDao
import io.beatmaps.common.dbo.IssueDao
import io.beatmaps.common.dbo.Playlist
import io.beatmaps.common.dbo.PlaylistDao
import io.beatmaps.common.dbo.Review
import io.beatmaps.common.dbo.ReviewDao
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.UserDao
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.dbo.VersionsDao
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
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Coalesce
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.lang.Integer.toHexString

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

fun Iterable<ResultRow>.preHydrate(isAdmin: Boolean) = also {
    mapNotNull { row ->
        row.getOrNull(Issue.data)
    }.groupBy { it.typeEnum }.forEach { (type, rows) ->
        when (type) {
            EIssueType.MapperApplication -> {}
            EIssueType.MapReport -> {
                Beatmap
                    .joinVersions {
                        if (isAdmin) {
                            Op.TRUE
                        } else {
                            Versions.state eq EMapState.Published
                        }
                    }
                    .joinUploader()
                    .joinCurator()
                    .selectAll()
                    .where {
                        (Beatmap.id inList rows.filterIsInstance<MapReportDataBase>().mapNotNull { it.id() }).let { q ->
                            if (isAdmin) {
                                q
                            } else {
                                q and Beatmap.deletedAt.isNull()
                            }
                        }
                    }
                    .complexToBeatmap()
            }

            EIssueType.UserReport -> {
                UserDao.wrapRows(
                    User
                        .selectAll()
                        .where {
                            User.id inList rows.filterIsInstance<UserReportDataBase>().map { it.userId }
                        }
                ).toList()
            }

            EIssueType.PlaylistReport -> {
                Playlist
                    .joinUser(Playlist.owner)
                    .joinPlaylistCurator()
                    .select(Playlist.columns + User.columns + curatorAlias.columns)
                    .where {
                        (Playlist.id inList rows.filterIsInstance<PlaylistReportDataBase>().map { it.playlistId }).let { q ->
                            if (isAdmin) {
                                q
                            } else {
                                q and Playlist.deletedAt.isNull() and (Playlist.type inList EPlaylistType.publicTypes)
                            }
                        }
                    }
                    .handleUser()
                    .handleCurator()
                    .forEach { PlaylistDao.wrapRow(it) }
            }

            EIssueType.ReviewReport -> {
                Review
                    .join(reviewerAlias, JoinType.INNER, Review.userId, reviewerAlias[User.id])
                    .join(Beatmap, JoinType.INNER, Review.mapId, Beatmap.id)
                    .joinVersions {
                        if (isAdmin) {
                            Op.TRUE
                        } else {
                            Versions.state eq EMapState.Published
                        }
                    }
                    .joinUploader()
                    .joinCurator()
                    .selectAll()
                    .where {
                        (Review.id inList rows.filterIsInstance<ReviewReportDataBase>().map { it.reviewId }).let { q ->
                            if (isAdmin) {
                                q
                            } else {
                                q and Review.deletedAt.isNull() and Beatmap.deletedAt.isNull()
                            }
                        }
                    }
                    .complexToReview()
            }
        }
    }
}

fun <P : EntityClass<ID, T>, ID : Comparable<ID>, T : Entity<ID>> P.cacheOnly(id: ID): T? =
    testCache(EntityID(id, table))

// Kinda dangerous, we don't recheck the content isn't deleted / unpublished but it shouldn't be in the cache if it is
fun IDbIssueData.hydrate(cdnPrefix: String): IHydratedIssueData = when (this) {
    is DbMapReportData -> HydratedMapReportData(id()?.let { mapId -> BeatmapDao.cacheOnly(mapId) }?.let { map -> MapDetail.from(map, cdnPrefix) }, this)
    is DbUserReportData -> HydratedUserReportData(UserDao.cacheOnly(userId)?.let { UserDetail.from(it, description = true) }, this)
    is DbPlaylistReportData -> HydratedPlaylistReportData(PlaylistDao.cacheOnly(playlistId)?.let { PlaylistFull.from(it, null, cdnPrefix) }, this)
    is DbReviewReportData -> HydratedReviewReportData(ReviewDao.cacheOnly(reviewId)?.let { ReviewDetail.from(it, cdnPrefix) }, this)
}

fun UserDao.toBasicInfo() = BasicUserInfo(
    id.value, uniqueName ?: name, description, avatar ?: UserDetail.getAvatar(uniqueName ?: name)
)

fun BeatmapDao.toBasicInfo(version: VersionsDao) = BasicMapInfo(
    toHexString(id.value),
    name,
    description,
    declaredAi,
    uploaded?.toKotlinInstant(),
    version.hash,
    uploader.toBasicInfo(),
    version.bpm,
    version.duration
)

fun PlaylistDao.toBasicInfo() = BasicPlaylistInfo(
    id.value,
    name,
    description,
    owner.toBasicInfo()
)

fun ReviewDao.toBasicInfo(version: VersionsDao) = BasicReviewInfo(
    id.value,
    text,
    ReviewSentiment.fromInt(sentiment),
    map.toBasicInfo(version),
    user.toBasicInfo(),
    createdAt.toKotlinInstant()
)

fun createDbIssue(type: EIssueType, id: Int): IDbIssueData = when (type) {
    EIssueType.MapperApplication -> null
    EIssueType.MapReport -> {
        Beatmap
            .joinVersions()
            .joinUploader()
            .select(
                Beatmap.id, Beatmap.name, Beatmap.description, Beatmap.uploader, Beatmap.uploaded, Beatmap.declaredAi,
                Versions.id, Versions.bpm, Versions.duration, Versions.hash,
                User.id, User.name, User.uniqueName, User.description, User.avatar
            )
            .where {
                Beatmap.deletedAt.isNull() and (Beatmap.id eq id)
            }
            .limit(1)
            .singleOrNull()
            ?.let { row ->
                UserDao.wrapRow(row) // Populate cache
                BeatmapDao.wrapRow(row).toBasicInfo(VersionsDao.wrapRow(row)).let { mi ->
                    DbMapReportData(mi.key, mi)
                }
            }
    }
    EIssueType.UserReport -> {
        User
            .select(User.id, User.name, User.uniqueName, User.description, User.avatar)
            .where {
                User.id eq id
            }
            .limit(1)
            .singleOrNull()
            ?.let { row ->
                DbUserReportData(id, UserDao.wrapRow(row).toBasicInfo())
            }
    }
    EIssueType.PlaylistReport -> {
        Playlist
            .joinUser(Playlist.owner)
            .select(
                Playlist.id, Playlist.name, Playlist.description, Playlist.owner,
                User.id, User.uniqueName, User.name, User.description, User.avatar
            )
            .where {
                (Playlist.id eq id) and Playlist.deletedAt.isNull() and (Playlist.type inList EPlaylistType.publicTypes)
            }
            .limit(1)
            .singleOrNull()
            ?.let { row ->
                UserDao.wrapRow(row) // Populate cache
                DbPlaylistReportData(id, PlaylistDao.wrapRow(row).toBasicInfo())
            }
    }
    EIssueType.ReviewReport -> {
        Review
            .join(reviewerAlias, JoinType.INNER, Review.userId, reviewerAlias[User.id])
            .join(Beatmap, JoinType.INNER, Review.mapId, Beatmap.id)
            .joinUploader()
            .joinVersions()
            .select(
                Review.id, Review.userId, Review.text, Review.createdAt, Review.sentiment, Review.mapId,
                Beatmap.id, Beatmap.name, Beatmap.description, Beatmap.uploader, Beatmap.uploaded, Beatmap.declaredAi,
                Versions.id, Versions.bpm, Versions.duration, Versions.hash,
                User.id, User.name, User.uniqueName, User.description, User.avatar,
                reviewerAlias[User.id], reviewerAlias[User.name], reviewerAlias[User.uniqueName], reviewerAlias[User.description], reviewerAlias[User.avatar]
            )
            .where {
                (Review.id eq id) and Review.deletedAt.isNull() and Beatmap.deletedAt.isNull()
            }
            .limit(1)
            .singleOrNull()
            ?.let { row ->
                UserDao.wrapRow(row, reviewerAlias) // Populate cache
                UserDao.wrapRow(row)
                BeatmapDao.wrapRow(row)
                DbReviewReportData(id, ReviewDao.wrapRow(row).toBasicInfo(VersionsDao.wrapRow(row)))
            }
    }
} ?: throw ServerApiException("Error hydrating issue")

fun IssueDetail.Companion.from(row: ResultRow, cdnPrefix: String, comments: Iterable<ResultRow>? = null) = from(IssueDao.wrapRow(row), cdnPrefix, comments)

fun IssueDetail.Companion.from(other: IssueDao, cdnPrefix: String, comments: Iterable<ResultRow>? = null) = IssueDetail(
    other.id.value,
    other.type,
    UserDetail.from(other.creator),
    other.createdAt.toKotlinInstant(),
    other.closedAt?.toKotlinInstant(),
    other.data.hydrate(cdnPrefix),
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

            val (res, issueId) = requireCaptcha(
                client,
                req.captcha,
                {
                    ActionResponse.success() to transaction {
                        Issue.insertAndGetId {
                            it[creator] = sess.userId
                            it[createdAt] = NowExpression(createdAt)
                            it[updatedAt] = NowExpression(updatedAt)
                            it[type] = req.type
                            it[data] = createDbIssue(req.type, req.id)
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
                                "You created a ${req.type.name} issue {$it}",
                                EAlertType.Issue,
                                sess.userId
                            )
                            updateAlertCount(sess.userId)
                        }
                    }
                }
            ) {
                it.toActionResponse() to null
            }

            if (issueId != null) {
                call.pub("beatmaps", "issues.$issueId.created", null, issueId)
                call.respond(HttpStatusCode.Created, issueId)
            } else {
                call.respond(HttpStatusCode.BadRequest, res)
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
                                val cond = Issue.creator eq sess?.userId

                                if (sess?.isCurator() == true) {
                                    q and ((Issue.type inList(EIssueType.curatorTypes)) or cond)
                                } else {
                                    q and cond
                                }
                            }
                        }
                    }
                    .handleUser()
                    .preHydrate(sess?.isAdmin() == true)
                    .singleOrNull() ?: throw NotFoundException()

                val comments = IssueComment
                    .joinUser(IssueComment.userId)
                    .selectAll()
                    .where { (IssueComment.issueId eq it.id) and IssueComment.deletedAt.isNull() and (if (sess?.isAdmin() == true) Op.TRUE else IssueComment.public) }
                    .orderBy(IssueComment.createdAt, SortOrder.ASC)
                    .handleUser()

                IssueDetail.from(issue, cdnPrefix(), comments)
            }

            call.respond(issue)
        }
    }

    post<IssueApi.IssueDetail> { req ->
        requireAuthorization { _, sess ->
            if (!sess.isCurator()) return@requireAuthorization ActionResponse.error("Unauthorised")

            val issueUpdate = call.receive<IssueUpdateRequest>()
            val success = transaction {
                Issue.update({
                    (Issue.id eq req.id).let { q ->
                        if (sess.isAdmin()) {
                            q
                        } else {
                            q and (Issue.type inList(EIssueType.curatorTypes))
                        }
                    }
                }) {
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
                        Issue.id, Issue.creator, Issue.type
                    )
                    ?.singleOrNull()
                    ?.let { IssueDao.wrapRow(it) }

                if (intermediaryResult == null) {
                    rollback()
                    return@newSuspendedTransaction ActionResponse.error("Issue not found")
                }

                val userPrivileged = sess.isAdmin() || (sess.isCurator() && EIssueType.curatorTypes.contains(intermediaryResult.type))

                if (req.commentId == null) {
                    requireCaptcha(
                        client,
                        comment.captcha,
                        {
                            if (!(userPrivileged || sess.userId == intermediaryResult.creatorId.value)) {
                                rollback()
                                return@requireCaptcha ActionResponse.error("Unauthorised")
                            }

                            IssueComment
                                .insertAndGetId {
                                    it[issueId] = req.id
                                    it[userId] = sess.userId
                                    it[text] = comment.text?.take(IssueConstants.MAX_COMMENT_LENGTH) ?: ""
                                    it[public] = if (!userPrivileged) { true } else { comment.public ?: false }
                                    it[createdAt] = NowExpression(createdAt)
                                    it[updatedAt] = NowExpression(updatedAt)
                                }

                            ActionResponse.success()
                        }
                    ) { it.toActionResponse() }
                } else {
                    val success = IssueComment
                        .update({
                            ((IssueComment.id eq req.commentId) and (IssueComment.issueId eq req.id)).let { q ->
                                if (userPrivileged) q else q.and(IssueComment.userId eq sess.userId)
                            }
                        }) {
                            comment.text?.let { txt -> it[text] = txt.take(IssueConstants.MAX_COMMENT_LENGTH) }
                            if (userPrivileged) comment.public?.let { p -> it[public] = p }
                        } > 0

                    if (success) ActionResponse.success() else ActionResponse.error()
                }
            }

            call.respond(if (response.success) HttpStatusCode.OK else HttpStatusCode.NotFound, response)
        }
    }

    get<IssueApi.IssueList> { req ->
        requireAuthorization { _, sess ->
            if (!sess.isCurator()) {
                call.respond(HttpStatusCode.Unauthorized)
                return@requireAuthorization
            }

            val admin = sess.isAdmin()

            val ans = transaction {
                Issue
                    .joinUser(Issue.creator)
                    .selectAll()
                    .where {
                        Op.TRUE
                            .let { q ->
                                if (!admin) {
                                    q and (Issue.type inList(EIssueType.curatorTypes))
                                } else {
                                    q
                                }
                            }
                            .notNull(req.type) { o -> Issue.type eq o }
                            .notNull(req.open) { o -> Issue.closedAt.run { if (o) isNull() else isNotNull() } }
                    }
                    .orderBy(Issue.updatedAt, SortOrder.DESC)
                    .limit(req.page, 30)
                    .handleUser()
                    .preHydrate(admin)
                    .map {
                        IssueDetail.from(it, cdnPrefix())
                    }
            }
            call.respond(ans)
        }
    }
}
