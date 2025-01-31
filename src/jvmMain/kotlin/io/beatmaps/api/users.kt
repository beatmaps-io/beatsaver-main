package io.beatmaps.api

import com.toxicbakery.bcrypt.Bcrypt
import de.nielsfalk.ktor.swagger.DefaultValue
import de.nielsfalk.ktor.swagger.Description
import de.nielsfalk.ktor.swagger.Ignore
import de.nielsfalk.ktor.swagger.notFound
import de.nielsfalk.ktor.swagger.ok
import de.nielsfalk.ktor.swagger.responds
import de.nielsfalk.ktor.swagger.version.shared.Group
import io.beatmaps.api.search.SolrSearchParams
import io.beatmaps.api.user.UserCrypto
import io.beatmaps.api.user.from
import io.beatmaps.api.user.getAvatar
import io.beatmaps.api.util.getWithOptions
import io.beatmaps.common.Config
import io.beatmaps.common.EmailChangedData
import io.beatmaps.common.PasswordChangedData
import io.beatmaps.common.RevokeSessionsData
import io.beatmaps.common.SuspendData
import io.beatmaps.common.UploadLimitData
import io.beatmaps.common.amqp.pub
import io.beatmaps.common.amqp.sendEmail
import io.beatmaps.common.api.ApiOrder
import io.beatmaps.common.api.EAlertType
import io.beatmaps.common.api.EDifficulty
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.api.EPlaylistType
import io.beatmaps.common.api.UserSearchSort
import io.beatmaps.common.db.DateMinusDays
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.db.countWithFilter
import io.beatmaps.common.db.length
import io.beatmaps.common.db.startsWith
import io.beatmaps.common.db.upsert
import io.beatmaps.common.dbo.Alert
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Collaboration
import io.beatmaps.common.dbo.Difficulty
import io.beatmaps.common.dbo.Follows
import io.beatmaps.common.dbo.ModLog
import io.beatmaps.common.dbo.OauthClient
import io.beatmaps.common.dbo.Patreon
import io.beatmaps.common.dbo.PatreonDao
import io.beatmaps.common.dbo.Playlist
import io.beatmaps.common.dbo.RefreshTokenTable
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.UserDao
import io.beatmaps.common.dbo.UserLog
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.common.dbo.handlePatreon
import io.beatmaps.common.dbo.joinPatreon
import io.beatmaps.common.dbo.joinUser
import io.beatmaps.common.dbo.joinVersions
import io.beatmaps.common.solr.SolrHelper
import io.beatmaps.common.solr.SolrResults
import io.beatmaps.common.solr.collections.UserSolr
import io.beatmaps.common.solr.field.apply
import io.beatmaps.common.solr.get
import io.beatmaps.common.solr.paged
import io.beatmaps.login.MongoClient
import io.beatmaps.login.MongoSession
import io.beatmaps.login.Session
import io.beatmaps.login.cookieName
import io.beatmaps.login.server.DBTokenStore
import io.beatmaps.util.optionalAuthorization
import io.beatmaps.util.requireAuthorization
import io.beatmaps.util.requireCaptcha
import io.beatmaps.util.updateAlertCount
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Header
import io.jsonwebtoken.Jwt
import io.jsonwebtoken.JwtBuilder
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.SignatureException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.locations.Location
import io.ktor.server.locations.delete
import io.ktor.server.locations.get
import io.ktor.server.locations.post
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.util.pipeline.PipelineContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.Sum
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.avg
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.intLiteral
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.min
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.union
import org.jetbrains.exposed.sql.update
import org.litote.kmongo.and
import org.litote.kmongo.descending
import org.litote.kmongo.div
import org.litote.kmongo.eq
import org.litote.kmongo.ne
import java.lang.Integer.toHexString
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Date
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

fun JwtBuilder.setExpiration(duration: Duration): JwtBuilder = setExpiration(Date.from(Clock.System.now().plus(duration).toJavaInstant()))
fun parseJwtUntrusted(jwt: String): Jwt<Header<*>, Claims> =
    jwt.substring(0, jwt.lastIndexOf('.') + 1).let {
        Jwts.parserBuilder().build().parseClaimsJwt(it)
    }

fun PatreonDao?.toTier() = if (this != null) {
    if (expireAt?.let { it.toKotlinInstant() >= Clock.System.now() } == true) {
        PatreonTier.fromPledge(pledge ?: 0)
    } else {
        PatreonTier.None
    }
} else {
    null
}

@Location("/api/users")
class UsersApi {
    @Location("/me")
    data class Me(val api: UsersApi)

    @Location("/username")
    data class Username(val api: UsersApi)

    @Location("/description")
    data class DescriptionApi(val api: UsersApi)

    @Location("/blur")
    data class BlurApi(val api: UsersApi)

    @Location("/admin")
    data class Admin(val api: UsersApi)

    @Location("/suspend")
    data class Suspend(val api: UsersApi)

    @Location("/register")
    data class Register(val api: UsersApi)

    @Location("/forgot")
    data class Forgot(val api: UsersApi)

    @Location("/reset")
    data class Reset(val api: UsersApi)

    @Location("/email")
    data class Email(val api: UsersApi)

    @Location("/change-email")
    data class ChangeEmail(val api: UsersApi)

    @Location("/sessions")
    data class Sessions(val api: UsersApi)

    @Location("/sessions/{id}")
    data class SessionsById(val id: String, val api: UsersApi)

    @Location("/id/{id}/playlist/{filename?}")
    data class UserPlaylist(val id: Int, val filename: String? = null, val collabs: Boolean = true, val api: UsersApi)

    @Location("/find/{id}")
    data class Find(val id: String, val api: UsersApi)

    @Location("/list/{page}")
    data class List(val page: Long = 0, val api: UsersApi)

    @Location("/follow")
    data class Follow(val api: UsersApi)

    @Location("/following/{user}/{page}")
    data class Following(val user: Int, val page: Long = 0, val api: UsersApi)

    @Location("/followedBy/{user}/{page}")
    data class FollowedBy(val user: Int, val page: Long = 0, val api: UsersApi)

    @Group("Users")
    @Location("/search/{page}")
    data class Search(
        val q: String? = "",
        val curator: Boolean? = null,
        val verified: Boolean? = null,
        val minUpvotes: Int? = null,
        val maxUpvotes: Int? = null,
        val minDownvotes: Int? = null,
        val maxDownvotes: Int? = null,
        val minMaps: Int? = null,
        val maxMaps: Int? = null,
        val minRankedMaps: Int? = null,
        val maxRankedMaps: Int? = null,
        val firstUploadBefore: Instant? = null,
        val firstUploadAfter: Instant? = null,
        val lastUploadBefore: Instant? = null,
        val lastUploadAfter: Instant? = null,
        @DefaultValue("0")
        val page: Long = 0,
        @Description("1 - 100") @DefaultValue("20")
        val pageSize: Int = 20,
        val sort: UserSearchSort = UserSearchSort.RELEVANCE,
        val order: ApiOrder = ApiOrder.DESC,
        @Ignore
        val api: UsersApi
    )

    @Location("/curators")
    data class Curators(val api: UsersApi)
}

fun followData(uploaderId: Int, userId: Int?): UserFollowData {
    val userFilter = Follows.userId eq uploaderId
    val followerFilter = Follows.followerId eq userId

    val userColumn = countWithFilter(userFilter)
    val followerColumn = if (userId != uploaderId) {
        Op.nullOp()
    } else {
        countWithFilter(followerFilter)
    }

    val followingColumn = countWithFilter(userFilter and followerFilter) greater intLiteral(0)
    fun followingColumn(column: Column<Boolean>) = countWithFilter(userFilter and followerFilter and column) greater intLiteral(0)
    val uploadColumn = followingColumn(Follows.upload)
    val curationColumn = followingColumn(Follows.curation)
    val collabColumn = followingColumn(Follows.collab)

    return Follows
        .select(userColumn, followerColumn, followingColumn, uploadColumn, curationColumn, collabColumn).where {
            Follows.following and (userFilter or followerFilter)
        }
        .single().let {
            UserFollowData(it[userColumn], it[followerColumn], it[followingColumn], it[uploadColumn], it[curationColumn], it[collabColumn])
        }
}

fun Route.userRoute(client: HttpClient) {
    val usernameRegex = Regex("^[._\\-A-Za-z0-9]{3,50}$")
    post<UsersApi.Username> {
        requireAuthorization { _, sess ->
            val req = call.receive<AccountDetailReq>()

            if (!usernameRegex.matches(req.textContent)) {
                throw UserApiException("Username not valid")
            } else {
                val success = transaction {
                    try {
                        User.update({ User.id eq sess.userId and (User.uniqueName.isNull() or User.renamedAt.lessEq(DateMinusDays(NowExpression(User.renamedAt), 1))) }) { u ->
                            u[uniqueName] = req.textContent
                            u[renamedAt] = Expression.build { case().When(uniqueName eq req.textContent, renamedAt).Else(NowExpression(renamedAt)) }
                            u[updatedAt] = NowExpression(updatedAt)
                        } > 0
                    } catch (_: ExposedSQLException) {
                        throw UserApiException("Username already taken")
                    }
                }

                success || throw UserApiException("You can only set a new username once per day")

                call.sessions.set(sess.copy(uniqueName = req.textContent))
                call.pub("beatmaps", "user.${sess.userId}.updated.name", null, sess.userId)
                call.respond(ActionResponse.success())
            }
        }
    }

    post<UsersApi.DescriptionApi> {
        requireAuthorization { _, sess ->
            val req = call.receive<AccountDetailReq>()

            val success = transaction {
                try {
                    User.update({ User.id eq sess.userId }) { u ->
                        u[description] = req.textContent.take(UserConstants.MAX_DESCRIPTION_LENGTH)
                        u[updatedAt] = NowExpression(updatedAt)
                    } > 0
                } catch (_: ExposedSQLException) {
                    false
                }
            }

            success || throw ServerApiException("Something went wrong")
            call.pub("beatmaps", "user.${sess.userId}.updated.info", null, sess.userId)
            call.respond(ActionResponse.success())
        }
    }

    post<UsersApi.BlurApi> {
        requireAuthorization { _, sess ->
            val req = call.receive<BlurReq>()

            transaction {
                try {
                    User.update({ User.id eq sess.userId }) { u ->
                        u[blurnsfw] = req.blur
                        u[updatedAt] = NowExpression(updatedAt)
                    } > 0
                } catch (_: ExposedSQLException) {
                    false
                }
            } || throw ServerApiException("Something went wrong")

            MongoClient.updateSessions(sess.userId, Session::blurnsfw, req.blur)
            call.respond(ActionResponse.success())
        }
    }

    post<UsersApi.Admin> {
        requireAuthorization { _, sess ->
            if (!sess.isAdmin()) {
                ActionResponse.error("Not an admin")
            } else {
                val req = call.receive<UserAdminRequest>()
                if (UserAdminRequest.allowedUploadSizes.contains(req.maxUploadSize) && UserAdminRequest.allowedVivifySizes.contains(req.maxVivifySize)) {
                    transaction {
                        fun runUpdate() =
                            User.update({
                                User.id eq req.userId
                            }) { u ->
                                u[uploadLimit] = req.maxUploadSize
                                u[vivifyLimit] = req.maxVivifySize
                                u[curator] = req.curator
                                u[seniorCurator] = req.curator && req.seniorCurator
                                u[verifiedMapper] = req.verifiedMapper
                                u[curatorTab] = req.curatorTab
                                u[updatedAt] = NowExpression(updatedAt)
                            } > 0

                        runUpdate().also {
                            if (it) {
                                ModLog.insert(
                                    sess.userId,
                                    null,
                                    UploadLimitData(req.maxUploadSize, req.curator, req.verifiedMapper, req.curatorTab, req.maxVivifySize),
                                    req.userId
                                )
                            }
                        }
                    }.let { success ->
                        if (success) {
                            MongoClient.updateSessions(req.userId, Session::curator, req.curator)

                            call.pub("beatmaps", "user.${req.userId}.updated.admin", null, req.userId)
                            ActionResponse.success()
                        } else {
                            ActionResponse.error("User not found")
                        }
                    }
                } else {
                    ActionResponse.error("Upload size not allowed")
                }
            }.let {
                call.respond(it)
            }
        }
    }

    post<UsersApi.Suspend> {
        requireAuthorization { _, sess ->
            if (!sess.isAdmin()) {
                ActionResponse.error("Not an admin")
            } else {
                val req = call.receive<UserSuspendRequest>()
                transaction {
                    fun runUpdate() =
                        User.update({
                            User.id eq req.userId
                        }) { u ->
                            if (req.suspended) {
                                u[suspendedAt] = NowExpression(suspendedAt)
                            } else {
                                u[suspendedAt] = null
                            }
                            u[updatedAt] = NowExpression(updatedAt)
                        } > 0

                    runUpdate().also {
                        if (it) {
                            ModLog.insert(
                                sess.userId,
                                null,
                                SuspendData(req.suspended, req.reason),
                                req.userId
                            )
                        }

                        if (req.suspended) {
                            Playlist.update({
                                Playlist.owner eq req.userId
                            }) { p ->
                                p[type] = EPlaylistType.Private
                            }
                        }
                    }
                }.let { success ->
                    if (success) {
                        MongoClient.updateSessions(req.userId, Session::suspended, req.suspended)

                        ActionResponse.success()
                    } else {
                        ActionResponse.error("User not found")
                    }
                }
            }.let {
                call.respond(it)
            }
        }
    }

    post<UsersApi.Register> {
        val req = call.receive<RegisterRequest>()

        val response = requireCaptcha(
            client,
            req.captcha,
            {
                if (req.password != req.password2) {
                    ActionResponse.error("Passwords don't match")
                } else if (req.password.length < 8) {
                    ActionResponse.error("Password too short")
                } else if (!usernameRegex.matches(req.username)) {
                    ActionResponse.error("Username not valid")
                } else {
                    try {
                        val bcrypt = String(Bcrypt.hash(req.password, 12))

                        val newUserId = transaction {
                            try {
                                User.insertAndGetId {
                                    it[name] = req.username
                                    it[email] = req.email
                                    it[password] = bcrypt
                                    it[verifyToken] = "pending"
                                    it[uniqueName] = req.username
                                    it[active] = false
                                } to null
                            } catch (e: ExposedSQLException) {
                                if (e.message?.contains("simple_username") == true) {
                                    // Username constraint -> show conflict error
                                    null to ActionResponse.error("Username taken")
                                } else if (e.message?.contains("uploader_pkey") == true) {
                                    // id constraint, retry transaction
                                    throw e
                                } else {
                                    // Email constraint -> show success message / check your email
                                    null to null
                                }
                            }
                        }

                        // Complicated series of fallbacks. If the id is set we created a news user, send them an email. If a response is set send it.
                        // Otherwise the email was a duplicate, tell the user via email so we don't reveal which emails have been registered already.
                        newUserId.first?.let {
                            val jwt = Jwts.builder()
                                .setExpiration(30.days)
                                .setSubject(it.value.toString())
                                .claim("action", "register")
                                .signWith(UserCrypto.key())
                                .compact()

                            sendEmail(
                                req.email,
                                "BeatSaver Account Verification",
                                "${req.username}\n\nTo verify your account, please click the link below:\n${Config.siteBase()}/verify/$jwt"
                            )

                            ActionResponse.success()
                        } ?: newUserId.second ?: run {
                            sendEmail(
                                req.email,
                                "BeatSaver Account",
                                "Someone just tried to create a new account at ${Config.siteBase()} with this email address but an account using this email already exists.\n\n" +
                                    "If this wasn't you then you can safely ignore this email otherwise please use a different email"
                            )

                            ActionResponse.success()
                        }
                    } catch (_: IllegalArgumentException) {
                        ActionResponse.error("Password too long")
                    }
                }
            }
        ) {
            it.toActionResponse()
        }

        call.respond(response)
    }

    post<UsersApi.Forgot> {
        val req = call.receive<ForgotRequest>()

        val response = requireCaptcha(
            client,
            req.captcha,
            {
                transaction {
                    User.selectAll().where {
                        (User.email eq req.email) and User.password.isNotNull() and (User.active or User.verifyToken.isNotNull())
                    }.firstOrNull()?.let { UserDao.wrapRow(it) }
                }?.let { user ->
                    val jwt = Jwts.builder()
                        .setExpiration(20.minutes)
                        .setSubject(user.id.toString())
                        .claim("action", "reset")
                        .signWith(UserCrypto.keyForUser(user))
                        .compact()

                    sendEmail(
                        req.email,
                        "BeatSaver Password Reset",
                        "You can reset your password for the account `${user.uniqueName}` by clicking here: ${Config.siteBase()}/reset/$jwt\n\n" +
                            "If this wasn't you then you can safely ignore this email."
                    )
                }

                ActionResponse.success()
            }
        ) {
            it.toActionResponse()
        }

        call.respond(response)
    }

    get<UsersApi.Sessions> {
        requireAuthorization { _, sess ->
            val oauthSessions = transaction {
                RefreshTokenTable
                    .join(OauthClient, JoinType.INNER, RefreshTokenTable.clientId, OauthClient.clientId)
                    .selectAll()
                    .where {
                        (RefreshTokenTable.userName eq sess.userId) and (RefreshTokenTable.expiration greater Clock.System.now().toJavaInstant())
                    }
                    .orderBy(RefreshTokenTable.expiration to SortOrder.DESC)
                    .map { row ->
                        OauthSession(
                            UserCrypto.encrypt(row[RefreshTokenTable.id].value),
                            row[OauthClient.name],
                            row[OauthClient.iconUrl],
                            row[RefreshTokenTable.scope].split(",").mapNotNull { scope -> OauthScope.fromTag(scope) },
                            row[RefreshTokenTable.expiration].toKotlinInstant()
                        )
                    }
            }

            val sessionId = call.request.cookies[cookieName]
            val siteSessions = if (MongoClient.connected) {
                MongoClient.sessions.find(MongoSession::session / Session::userId eq sess.userId)
                    .sort(descending(MongoSession::expireAt))
                    .map { row ->
                        SiteSession(
                            UserCrypto.encrypt(row.id),
                            row.session.countryCode,
                            row.expireAt,
                            row.id == sessionId
                        )
                    }.toList()
            } else { listOf() }

            call.respond(SessionsData(oauthSessions, siteSessions))
        }
    }

    delete<UsersApi.Sessions> {
        requireAuthorization { _, sess ->
            val req = call.receive<SessionRevokeRequest>()
            val userId = req.userId ?: sess.userId
            val sessionId = call.request.cookies[cookieName]

            val response = if (userId != sess.userId && !sess.isAdmin()) {
                ActionResponse.error("Not an admin or no reason given")
            } else {
                transaction {
                    if (userId != sess.userId) {
                        ModLog.insert(
                            sess.userId,
                            null,
                            RevokeSessionsData(true, req.reason),
                            userId
                        )
                    }

                    if (req.site != true) {
                        DBTokenStore.deleteForUser(userId)
                    }

                    if (req.site != false && MongoClient.connected) {
                        MongoClient.sessions.deleteMany(
                            and(MongoSession::id ne sessionId, MongoSession::session / Session::userId eq userId)
                        )
                    }
                }

                ActionResponse.success()
            }

            call.respond(response)
        }
    }

    delete<UsersApi.SessionsById> { byId ->
        requireAuthorization { _, sess ->
            val req = call.receive<SessionRevokeRequest>()
            val userId = req.userId ?: sess.userId
            val id = UserCrypto.decrypt(byId.id)
            val site = req.site

            val response = if (userId != sess.userId && !sess.isAdmin()) {
                ActionResponse.error("Not an admin")
            } else if (site == null) {
                ActionResponse.error("site property is required when deleting by id")
            } else {
                transaction {
                    if (userId != sess.userId) {
                        ModLog.insert(
                            sess.userId,
                            null,
                            RevokeSessionsData(false, req.reason),
                            userId
                        )
                    }

                    if (!site) {
                        DBTokenStore.revokeRefreshToken(id)
                        ActionResponse.success()
                    } else if (!MongoClient.connected) {
                        ActionResponse.error("Can't revoke in memory sessions")
                    } else if (id == call.request.cookies[cookieName]) {
                        ActionResponse.error("Can't revoke current session")
                    } else {
                        MongoClient.sessions.deleteOne(
                            MongoSession::id eq id
                        )
                        ActionResponse.success()
                    }
                }
            }

            call.respond(response)
        }
    }

    post<UsersApi.Email> {
        requireAuthorization { _, sess ->
            val req = call.receive<EmailRequest>()

            val response = requireCaptcha(
                client,
                req.captcha,
                {
                    newSuspendedTransaction {
                        User.selectAll().where {
                            (User.id eq sess.userId)
                        }.firstOrNull()?.let { UserDao.wrapRow(it) }
                    }?.let { user ->
                        if (user.emailChangedAt.toKotlinInstant() > Clock.System.now().minus(10.days)) {
                            ActionResponse.error("You can only change email once every 10 days")
                        } else {
                            val jwt = Jwts.builder()
                                .setExpiration(20.minutes)
                                .setSubject(user.id.toString())
                                .claim("email", req.email)
                                .claim("action", "email")
                                .signWith(UserCrypto.key())
                                .compact()

                            sendEmail(
                                req.email,
                                "BeatSaver Email Change",
                                "Hi ${user.uniqueName},\n\n" +
                                    "You can update the email on your account by clicking here: ${Config.siteBase()}/change-email/$jwt"
                            )

                            ActionResponse.success()
                        }
                    } ?: ActionResponse.error("User not found")
                }
            ) {
                it.toActionResponse()
            }

            call.respond(if (response.success) HttpStatusCode.OK else HttpStatusCode.BadRequest, response)
        }
    }

    fun PipelineContext<*, ApplicationCall>.sendReclaimMail(user: UserDao) =
        user.email?.let {
            val jwt = Jwts.builder()
                .setExpiration(7.days)
                .setSubject(user.id.toString())
                .claim("email", user.email)
                .claim("action", "reclaim")
                .signWith(UserCrypto.key())
                .compact()

            sendEmail(
                it,
                "BeatSaver Email Changed",
                "Hi ${user.uniqueName ?: user.name},\n\n" +
                    "Your email address for accessing ${Config.siteBase()} was changed.\n\n" +
                    "If this wasn't you then you can click here to change it back: ${Config.siteBase()}/change-email/$jwt\n\n" +
                    "If you are still having trouble accessing your account get in touch with us on discord " +
                    "( https://discord.gg/rjVDapkMmj ) or email support@beatsaver.com"
            )
        }

    post<UsersApi.ChangeEmail> {
        val req = call.receive<ChangeEmailRequest>()

        try {
            val untrusted = parseJwtUntrusted(req.jwt)

            val userId = untrusted.body.subject.toInt()
            val newEmail = untrusted.body.get("email", String::class.java)
            val action = untrusted.body.get("action", String::class.java)

            newSuspendedTransaction {
                User.selectAll().where {
                    User.id eq userId
                }.firstOrNull()?.let { UserDao.wrapRow(it) }?.let { user ->
                    try {
                        // Check the user knows the current account password
                        val isReclaim = action == "reclaim"
                        if (user.email == newEmail) {
                            // Ignore if this is a re-do
                            ActionResponse.success()
                        } else if (!isReclaim && user.emailChangedAt.toKotlinInstant() > Clock.System.now().minus(10.days)) {
                            ActionResponse.error("You can only change email once every 10 days")
                        } else if (isReclaim || user.password?.let { curPw -> Bcrypt.verify(req.password, curPw.toByteArray()) } == true) {
                            // If the jwt is valid we can change the users email
                            Jwts.parserBuilder()
                                .setSigningKey(UserCrypto.key())
                                .build()
                                .parseClaimsJws(req.jwt)

                            if (!listOf("email", "reclaim").contains(action)) throw JwtException("Bad claim")

                            val success = User.update({
                                User.id eq userId
                            }) {
                                it[email] = newEmail
                                it[emailChangedAt] = NowExpression(emailChangedAt)
                                it[updatedAt] = NowExpression(updatedAt)
                            } > 0

                            if (success) {
                                UserLog.insert(userId, null, EmailChangedData(user.email, newEmail))

                                // Log out user as sessions contain their email so they are now invalid
                                DBTokenStore.deleteForUser(userId)
                                MongoClient.deleteSessionsFor(userId)

                                if (!isReclaim) {
                                    sendReclaimMail(user)
                                }

                                ActionResponse.success()
                            } else {
                                ActionResponse.error("Failed to update email")
                            }
                        } else {
                            ActionResponse.error("Current password incorrect")
                        }
                    } catch (_: ExposedSQLException) {
                        ActionResponse.error("Email in use on another account")
                    } catch (_: SignatureException) {
                        ActionResponse.error("Token no longer valid")
                    } catch (_: JwtException) {
                        ActionResponse.error("Bad token")
                    }
                } ?: ActionResponse.error("User not found")
            }
        } catch (_: IllegalArgumentException) {
            ActionResponse.error("Password too long")
        } catch (_: ExpiredJwtException) {
            ActionResponse.error("Link has expired")
        } catch (_: JwtException) {
            ActionResponse.error("Token is malformed")
        }.let {
            call.respond(it)
        }
    }

    post<UsersApi.Reset> {
        val req = call.receive<ResetRequest>()

        val response = if (req.password != req.password2) {
            ActionResponse.error("Passwords don't match")
        } else if (req.password.length < 8) {
            ActionResponse.error("Password too short")
        } else {
            try {
                val bcrypt = String(Bcrypt.hash(req.password, 12))
                val untrusted = parseJwtUntrusted(req.jwt)

                untrusted.body.subject.toInt().let { userId ->
                    transaction {
                        User.selectAll().where {
                            User.id eq userId
                        }.firstOrNull()?.let { UserDao.wrapRow(it) }?.let { user ->
                            // If the jwt is valid we can reset the user's password :D
                            try {
                                Jwts.parserBuilder()
                                    .require("action", "reset")
                                    .setSigningKey(UserCrypto.keyForUser(user))
                                    .build()
                                    .parseClaimsJws(req.jwt)

                                val success = User.update({
                                    User.id eq userId
                                }) {
                                    it[password] = bcrypt

                                    // The user must have received an email to reset their password so
                                    // we can also activate their account
                                    it[verifyToken] = null
                                    it[active] = true
                                    it[updatedAt] = NowExpression(updatedAt)
                                } > 0

                                if (success) {
                                    UserLog.insert(userId, null, PasswordChangedData)

                                    // Revoke all logins
                                    DBTokenStore.deleteForUser(userId)
                                    MongoClient.deleteSessionsFor(userId)

                                    ActionResponse.success()
                                } else {
                                    ActionResponse.error("Failed to update password")
                                }
                            } catch (_: SignatureException) {
                                // As previous password is included in key the signature will fail if the password
                                // has changed since we sent the link
                                ActionResponse.error("Reset token no longer valid")
                            } catch (_: JwtException) {
                                ActionResponse.error("Bad token")
                            }.let { it to user.active }
                        } ?: (ActionResponse.error("User not found") to false)
                    }.let { (response, previousActive) ->
                        if (response.success && !previousActive) call.pub("beatmaps", "user.$userId.updated.active", null, userId)
                        response
                    }
                }
            } catch (_: IllegalArgumentException) {
                ActionResponse.error("Password too long")
            } catch (_: ExpiredJwtException) {
                ActionResponse.error("Password reset link has expired")
            } catch (_: JwtException) {
                ActionResponse.error("Reset token is malformed")
            }
        }

        call.respond(response)
    }

    post<UsersApi.Me> {
        requireAuthorization { _, sess ->
            val req = call.receive<AccountRequest>()
            val newPassword = req.password
            val currentPassword = req.currentPassword

            val response = if (newPassword == null || currentPassword == null) {
                // Not a password reset request
                ActionResponse.success()
            } else if (newPassword != req.password2) {
                ActionResponse.error("Passwords don't match")
            } else if (newPassword.length < 8) {
                ActionResponse.error("Password too short")
            } else {
                try {
                    val bcrypt = String(Bcrypt.hash(newPassword, 12))

                    transaction {
                        User.selectAll().where {
                            User.id eq sess.userId
                        }.firstOrNull()?.let { r ->
                            if (r[User.password]?.let { curPw -> Bcrypt.verify(currentPassword, curPw.toByteArray()) } == true) {
                                User.update({
                                    User.id eq sess.userId
                                }) {
                                    it[password] = bcrypt
                                    it[updatedAt] = NowExpression(updatedAt)
                                }
                                DBTokenStore.deleteForUser(sess.userId)
                                ActionResponse.success()
                            } else {
                                ActionResponse.error("Current password incorrect")
                            }
                        } ?: ActionResponse.error("Account not found") // Shouldn't ever happen
                    }
                } catch (_: IllegalArgumentException) {
                    ActionResponse.error("Password too long")
                }
            }

            call.respond(response)
        }
    }

    post<UsersApi.Follow> {
        requireAuthorization(OauthScope.MANAGE_FOLLOW) { _, user ->
            val req = call.receive<UserFollowRequest>()

            if (req.userId == user.userId && req.following) {
                throw UserApiException("Can't follow yourself")
            }

            transaction {
                val shouldAlert = Follows.selectAll().where { (Follows.userId eq req.userId) and (Follows.followerId eq user.userId) }.empty()

                Follows.upsert(conflictIndex = Follows.link) { follow ->
                    follow[userId] = req.userId
                    follow[followerId] = user.userId
                    follow[since] = NowExpression(since)
                    follow[upload] = req.upload
                    follow[curation] = req.curation
                    follow[collab] = req.collab
                    follow[following] = req.following
                }
                if (shouldAlert) {
                    val followedUser = UserDao.wrapRow(User.selectAll().where { User.id eq req.userId }.single())

                    if (followedUser.followAlerts) {
                        Alert.insert(
                            "New Follower",
                            "@${user.uniqueName} is now following you!",
                            EAlertType.Follow,
                            req.userId
                        )
                        updateAlertCount(req.userId)
                    }
                }
            }
            call.respond(HttpStatusCode.OK)
        }
    }

    get<UsersApi.Find> {
        val user = transaction {
            User.selectAll().where {
                User.hash.eq(it.id) and User.active
            }.firstOrNull()?.let { row -> UserDetail.from(row) }
        }

        if (user == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respond(user)
        }
    }

    get<UsersApi.List> { req ->
        val us = transaction {
            val userAlias = User.select(User.upvotes, User.id, User.name, User.uniqueName, User.description, User.avatar, User.hash, User.discordId).where {
                Op.TRUE and User.active
            }.orderBy(User.upvotes, SortOrder.DESC).limit(req.page).alias("u")

            val query = userAlias
                .join(Beatmap, JoinType.INNER, userAlias[User.id], Beatmap.uploader) {
                    Beatmap.deletedAt.isNull()
                }
                .join(Versions, JoinType.INNER, onColumn = Beatmap.id, otherColumn = Versions.mapId, additionalConstraint = { Versions.state eq EMapState.Published })
                .select(
                    Beatmap.uploader,
                    Beatmap.id.count(),
                    userAlias[User.id],
                    userAlias[User.upvotes],
                    userAlias[User.name],
                    userAlias[User.uniqueName],
                    userAlias[User.description],
                    userAlias[User.avatar],
                    userAlias[User.hash],
                    userAlias[User.discordId],
                    Beatmap.downVotesInt.sum(),
                    Beatmap.bpm.avg(),
                    Beatmap.score.avg(3),
                    Beatmap.duration.avg(0),
                    countWithFilter(Beatmap.ranked or Beatmap.blRanked),
                    Beatmap.uploaded.min(),
                    Beatmap.uploaded.max()
                )
                .groupBy(Beatmap.uploader, userAlias[User.id], userAlias[User.upvotes], userAlias[User.name], userAlias[User.uniqueName], userAlias[User.description], userAlias[User.avatar], userAlias[User.hash], userAlias[User.discordId])
                .orderBy(userAlias[User.upvotes], SortOrder.DESC)

            query.toList().map {
                val dao = UserDao.wrapRow(it, userAlias)

                val uniqueName = dao.uniqueName
                UserDetail(
                    it[Beatmap.uploader].value,
                    uniqueName ?: dao.name,
                    dao.description,
                    uniqueName != null,
                    avatar = UserDetail.getAvatar(dao),
                    stats = UserStats(
                        dao.upvotes,
                        it[Beatmap.downVotesInt.sum()] ?: 0,
                        it[Beatmap.id.count()].toInt(),
                        it[countWithFilter(Beatmap.ranked or Beatmap.blRanked)],
                        it[Beatmap.bpm.avg()]?.toFloat() ?: 0f,
                        it[Beatmap.score.avg(3)]?.movePointRight(2)?.toFloat() ?: 0f,
                        it[Beatmap.duration.avg(0)]?.toFloat() ?: 0f,
                        it[Beatmap.uploaded.min()]?.toKotlinInstant(),
                        it[Beatmap.uploaded.max()]?.toKotlinInstant()
                    ),
                    type = if (dao.discordId != null) AccountType.DISCORD else AccountType.SIMPLE
                )
            }
        }

        call.respond(UserSearchResponse(us))
    }

    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    get<UsersApi.UserPlaylist> {
        val (maps, user) = transaction {
            Beatmap.joinVersions()
                .selectAll().where {
                    Beatmap.id.inSubQuery(
                        Beatmap.select(Beatmap.id).where { (Beatmap.uploader eq it.id) and Beatmap.deletedAt.isNull() }
                            .let { q ->
                                if (it.collabs) {
                                    q.union(Collaboration.select(Collaboration.mapId).where { Collaboration.collaboratorId eq it.id and Collaboration.accepted })
                                } else {
                                    q
                                }
                            }
                    )
                }.complexToBeatmap().sortedByDescending { b -> b.uploaded } to User.selectAll().where { User.id eq it.id and User.active }.firstOrNull()?.let { row -> UserDetail.from(row) }
        }

        if (user == null) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }

        val playlistSongs = maps.mapNotNull { map ->
            map.versions.values.firstOrNull { v -> v.state == EMapState.Published }?.let { v ->
                PlaylistSong(
                    toHexString(map.id.value),
                    v.hash,
                    map.name
                )
            }
        }

        val imageStr = Base64.getEncoder().encodeToString(
            client.get(user.avatar) {
                timeout {
                    socketTimeoutMillis = 30000
                    requestTimeoutMillis = 60000
                }
            }.body<ByteArray>()
        )

        val dateStr = formatter.format(LocalDateTime.now())

        call.response.headers.append(HttpHeaders.ContentDisposition, "attachment; filename=\"${user.name}-$dateStr.bplist\"")
        call.respond(
            Playlist(
                "Maps by ${user.name} (${playlistSongs.size} Total)",
                user.name,
                "All maps by ${user.name} ($dateStr)",
                imageStr,
                PlaylistCustomData("${Config.apiBase(true)}/users/id/${it.id}/playlist"),
                playlistSongs
            )
        )
    }

    fun statsForUser(user: UserDao) = transaction {
        val statTmp =
            Beatmap
                .join(Versions, JoinType.INNER, Beatmap.id, Versions.mapId) {
                    Versions.state eq EMapState.Published
                }
                .select(
                    Beatmap.id.count(),
                    Beatmap.upVotesInt.sum(),
                    Beatmap.downVotesInt.sum(),
                    Beatmap.bpm.avg(),
                    Beatmap.score.avg(3),
                    Beatmap.duration.avg(0),
                    countWithFilter(Beatmap.ranked or Beatmap.blRanked),
                    Beatmap.uploaded.min(),
                    Beatmap.uploaded.max()
                ).where {
                    (Beatmap.uploader eq user.id) and (Beatmap.deletedAt.isNull())
                }.first().let {
                    UserStats(
                        it[Beatmap.upVotesInt.sum()] ?: 0,
                        it[Beatmap.downVotesInt.sum()] ?: 0,
                        it[Beatmap.id.count()].toInt(),
                        it[countWithFilter(Beatmap.ranked or Beatmap.blRanked)],
                        it[Beatmap.bpm.avg()]?.toFloat() ?: 0f,
                        it[Beatmap.score.avg(3)]?.movePointRight(2)?.toFloat() ?: 0f,
                        it[Beatmap.duration.avg(0)]?.toFloat() ?: 0f,
                        it[Beatmap.uploaded.min()]?.toKotlinInstant(),
                        it[Beatmap.uploaded.max()]?.toKotlinInstant()
                    )
                }

        val cases = EDifficulty.entries.associateWith { diffCase(it) }
        val diffStats = Difficulty
            .join(Beatmap, JoinType.INNER, Beatmap.id, Difficulty.mapId)
            .join(Versions, JoinType.INNER, Difficulty.versionId, Versions.id) {
                Versions.state eq EMapState.Published
            }
            .select(Difficulty.id.count(), *cases.values.toTypedArray())
            .where {
                (Beatmap.uploader eq user.id) and (Beatmap.deletedAt.isNull())
            }.first().let {
                fun safeGetCount(diff: EDifficulty) = cases[diff]?.let { c -> it.getOrNull(c) } ?: 0
                UserDiffStats(
                    it[Difficulty.id.count()].toInt(),
                    safeGetCount(EDifficulty.Easy),
                    safeGetCount(EDifficulty.Normal),
                    safeGetCount(EDifficulty.Hard),
                    safeGetCount(EDifficulty.Expert),
                    safeGetCount(EDifficulty.ExpertPlus)
                )
            }

        statTmp.copy(diffStats = diffStats)
    }

    fun userBy(where: SqlExpressionBuilder.() -> Op<Boolean>) =
        UserDao.wrapRow(User.joinPatreon().selectAll().where(where).handlePatreon().firstOrNull() ?: throw NotFoundException())

    get<UsersApi.Me> {
        requireAuthorization { _, sess ->
            val detail = transaction {
                val user = userBy {
                    User.id eq sess.userId
                }

                val dualAccount = user.discordId != null && user.email != null && user.uniqueName != null
                val followData = followData(sess.userId, sess.userId)

                UserDetail.from(user, stats = statsForUser(user), followData = followData, description = true, patreon = true).let { usr ->
                    val tmp = usr.copy(email = user.email, blurnsfw = user.blurnsfw)
                    if (dualAccount) {
                        tmp.copy(type = AccountType.DUAL)
                    } else {
                        tmp
                    }
                }
            }

            call.respond(detail)
        }
    }

    getWithOptions<MapsApi.UserId>("Get user info".responds(ok<UserDetail>(), notFound())) {
        optionalAuthorization(OauthScope.FOLLOW) { _, sess ->
            val userDetail = transaction {
                val user = userBy {
                    (User.id eq it.id) and User.active
                }
                val followData = followData(user.id.value, sess?.userId)

                UserDetail.from(user, stats = statsForUser(user), followData = followData, description = true, patreon = true).let {
                    if (call.sessions.get<Session>()?.isAdmin() == true) {
                        it.copy(uploadLimit = user.uploadLimit, vivifyLimit = user.vivifyLimit)
                    } else {
                        it
                    }
                }
            }

            call.respond(userDetail)
        }
    }

    getWithOptions<MapsApi.UserIds>("Get user info".responds(ok<UserDetail>(), notFound())) {
        val ids = it.ids.split(",").mapNotNull { id -> id.toIntOrNull() }.take(50)

        val userDetail = transaction {
            User
                .selectAll()
                .where {
                    (User.id inList ids) and User.active
                }
                .map { row ->
                    UserDetail.from(row)
                }
        }

        call.respond(userDetail)
    }

    getWithOptions<MapsApi.UserName>("Get user info by name".responds(ok<UserDetail>(), notFound())) {
        val userDetail = transaction {
            val user = userBy {
                (User.uniqueName eq it.name) and User.active
            }

            UserDetail.from(user, stats = statsForUser(user), description = true, patreon = true)
        }

        call.respond(userDetail)
    }

    fun getFollowerData(page: Long, joinOn: Column<EntityID<Int>>, condition: SqlExpressionBuilder.() -> Op<Boolean>) = transaction {
        val followsSubquery = Follows
            .select(joinOn, Follows.since)
            .where { condition() and Follows.following }
            .limit(page)
            .orderBy(Follows.since, SortOrder.DESC)
            .alias("fs")

        followsSubquery
            .joinUser(followsSubquery[joinOn])
            .joinPatreon()
            .join(Beatmap, JoinType.LEFT, User.id, Beatmap.uploader) {
                Beatmap.deletedAt.isNull()
            }
            .join(Versions, JoinType.LEFT, onColumn = Beatmap.id, otherColumn = Versions.mapId, additionalConstraint = { Versions.state eq EMapState.Published })
            .select(
                User.columns.plus(Versions.mapId.count()).plus(Patreon.columns)
            )
            .groupBy(User.id, Patreon.id, followsSubquery[Follows.since])
            .orderBy(followsSubquery[Follows.since], SortOrder.DESC)
            .handlePatreon()
            .map { row ->
                UserDetail.from(row, stats = UserStats(totalMaps = row[Versions.mapId.count()].toInt()), patreon = true)
            }
    }

    get<UsersApi.Following> {
        val users = getFollowerData(it.page, Follows.followerId) {
            Follows.userId eq it.user
        }

        call.respond(users)
    }

    get<UsersApi.FollowedBy> {
        requireAuthorization(OauthScope.FOLLOW) { _, sess ->
            if (it.user != sess.userId) call.respond(HttpStatusCode.Forbidden, ActionResponse.error())

            val users = getFollowerData(it.page, Follows.userId) {
                Follows.followerId eq it.user
            }

            call.respond(users)
        }
    }

    fun legacySearch(q: String?) = UserSearchResponse(
        transaction {
            User
                .selectAll()
                .where {
                    User.uniqueName startsWith q and User.active
                }
                .orderBy(length(User.uniqueName), SortOrder.ASC)
                .limit(10)
                .map { row ->
                    UserDetail.from(row)
                }
        }
    )

    getWithOptions<UsersApi.Search>("Search for users".responds(ok<UserSearchResponse>())) { req ->
        if (!SolrHelper.enabled) {
            call.respond(legacySearch(req.q))
            return@getWithOptions
        }

        val searchInfo = (req.q ?: "").let { query -> SolrSearchParams(query, query, listOf()) }

        newSuspendedTransaction {
            val response = UserSolr.newQuery()
                .let { q ->
                    searchInfo.applyQuery(q)
                }
                .let { q ->
                    UserSolr.addSortArgs(q, req.sort, req.order)
                }
                .notNull(req.curator) { o -> UserSolr.curator eq o }
                .notNull(req.verified) { o -> UserSolr.verifiedMapper eq o }
                .also { q ->
                    q.apply(UserSolr.totalUpvotes.betweenNullableInc(req.minUpvotes, req.maxUpvotes))
                    q.apply(UserSolr.totalDownvotes.betweenNullableInc(req.minDownvotes, req.maxDownvotes))
                    q.apply(UserSolr.totalMaps.betweenNullableInc(req.minMaps, req.maxMaps))
                    q.apply(UserSolr.rankedMaps.betweenNullableInc(req.minRankedMaps, req.maxRankedMaps))

                    q.apply(UserSolr.firstUpload.betweenNullableInc(req.firstUploadAfter, req.firstUploadBefore))
                    q.apply(UserSolr.lastUpload.betweenNullableInc(req.lastUploadAfter, req.lastUploadBefore))
                }
                .paged(req.page.toInt(), req.pageSize.coerceIn(1, 100))
                .let { UserSolr.query(it) }

            val userIds = response.results.mapNotNull { it[UserSolr.id] }
            val statsLookup = response.results.associateBy { it[UserSolr.id] }
            val numRecords = response.results.numFound.toInt()
            val results = SolrResults(userIds, response.qTime, numRecords)

            val users = User
                .selectAll()
                .where {
                    User.id inList results.mapIds and User.active
                }
                .map { row ->
                    val statsFromSolr = statsLookup[row[User.id].value]?.let { s ->
                        UserStats(
                            s[UserSolr.totalUpvotes],
                            s[UserSolr.totalDownvotes],
                            s[UserSolr.totalMaps],
                            s[UserSolr.rankedMaps],
                            s[UserSolr.avgBpm],
                            s[UserSolr.avgScore],
                            s[UserSolr.avgDuration],
                            s[UserSolr.firstUpload],
                            s[UserSolr.lastUpload]
                        )
                    }

                    UserDetail.from(
                        row,
                        stats = statsFromSolr
                    )
                }
                .sortedBy { results.order[it.id] }

            call.respond(UserSearchResponse(users, results.searchInfo))
        }
    }

    getWithOptions<UsersApi.Curators> {
        val users = transaction {
            User
                .selectAll()
                .where { User.curator eq Op.TRUE }
                .orderBy(User.seniorCurator to SortOrder.DESC, User.uniqueName to SortOrder.ASC)
                .limit(50)
                .map { row ->
                    UserDetail.from(row, description = true)
                }
        }

        call.respond(users)
    }
}

fun diffCase(diff: EDifficulty) = Sum(Expression.build { case().When(Difficulty.difficulty eq diff, intLiteral(1)).Else(intLiteral(0)) }, IntegerColumnType())
