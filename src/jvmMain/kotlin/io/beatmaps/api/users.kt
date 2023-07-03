package io.beatmaps.api

import com.toxicbakery.bcrypt.Bcrypt
import de.nielsfalk.ktor.swagger.get
import de.nielsfalk.ktor.swagger.notFound
import de.nielsfalk.ktor.swagger.ok
import de.nielsfalk.ktor.swagger.responds
import io.beatmaps.common.Config
import io.beatmaps.common.SuspendData
import io.beatmaps.common.UploadLimitData
import io.beatmaps.common.api.EDifficulty
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.api.EPlaylistType
import io.beatmaps.common.client
import io.beatmaps.common.db.DateMinusDays
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.db.countWithFilter
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Difficulty
import io.beatmaps.common.dbo.Follows
import io.beatmaps.common.dbo.ModLog
import io.beatmaps.common.dbo.OauthClient
import io.beatmaps.common.dbo.Playlist
import io.beatmaps.common.dbo.RefreshTokenTable
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.UserDao
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.common.dbo.joinVersions
import io.beatmaps.common.sendEmail
import io.beatmaps.login.DBTokenStore
import io.beatmaps.login.MongoClient
import io.beatmaps.login.MongoSession
import io.beatmaps.login.Session
import io.beatmaps.login.cookieName
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.SignatureException
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.locations.Location
import io.ktor.server.locations.delete
import io.ktor.server.locations.get
import io.ktor.server.locations.options
import io.ktor.server.locations.post
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.util.hex
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.Sum
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.avg
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.intLiteral
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.min
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.litote.kmongo.div
import org.litote.kmongo.eq
import java.lang.Integer.toHexString
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Date
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.time.DurationUnit
import kotlin.time.toDuration

fun md5(input: String): String {
    val md = MessageDigest.getInstance("MD5")
    return BigInteger(1, md.digest(input.toByteArray())).toString(16).padStart(32, '0')
}

fun UserDetail.Companion.getAvatar(other: UserDao) = other.avatar ?: "https://www.gravatar.com/avatar/${other.hash ?: md5(other.uniqueName ?: other.name)}?d=retro"

fun UserDetail.Companion.from(other: UserDao, roles: Boolean = false, stats: UserStats? = null, followData: UserFollowData? = null, description: Boolean = false) =
    UserDetail(
        other.id.value, other.uniqueName ?: other.name, if (description) other.description else null, other.uniqueName != null, other.hash, if (roles) other.testplay else null,
        getAvatar(other), stats, followData, if (other.discordId != null) AccountType.DISCORD else AccountType.SIMPLE,
        admin = other.admin, curator = other.curator, verifiedMapper = other.verifiedMapper, suspendedAt = other.suspendedAt?.toKotlinInstant(),
        playlistUrl = "${Config.apiBase(true)}/users/id/${other.id.value}/playlist"
    )

fun UserDetail.Companion.from(row: ResultRow, roles: Boolean = false) = from(UserDao.wrapRow(row), roles)
actual object UserDetailHelper {
    actual fun profileLink(userDetail: UserDetail, tab: String?, absolute: Boolean) = Config.siteBase(absolute) + "/profile/${userDetail.id}" + (tab?.let { "#$it" } ?: "")
}

object UserCrypto {
    private val secret = System.getenv("BSHASH_SECRET")?.let { Base64.getDecoder().decode(it) } ?: hex("f1d2959be6ac1a5c457cebd9837cacad")
    private val secretEncryptKey = SecretKeySpec(secret, "AES")
    private val ephemeralIv = ByteArray(secretEncryptKey.encoded.size).apply { SecureRandom().nextBytes(this) }

    fun keyForUser(user: UserDao): java.security.Key = Keys.hmacShaKeyFor(secret + "${user.password}-${user.createdAt}".toByteArray())

    private fun encryptDecrypt(mode: Int, input: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
        cipher.init(mode, secretEncryptKey, IvParameterSpec(iv))
        return cipher.doFinal(input)
    }

    fun encrypt(input: String, iv: ByteArray = ephemeralIv) = hex(encryptDecrypt(Cipher.ENCRYPT_MODE, input.toByteArray(), iv))
    fun decrypt(input: String, iv: ByteArray = ephemeralIv) = String(encryptDecrypt(Cipher.DECRYPT_MODE, hex(input), iv))

    fun getHash(userId: String, salt: ByteArray = secret) = MessageDigest.getInstance("SHA1").let {
        it.update(salt + userId.toByteArray())
        hex(it.digest())
    }
}

@Location("/api/users")
class UsersApi {
    @Location("/me")
    data class Me(val api: UsersApi)

    @Location("/username")
    data class Username(val api: UsersApi)

    @Location("/description")
    data class Description(val api: UsersApi)

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

    @Location("/sessions")
    data class Sessions(val api: UsersApi)

    @Location("/id/{id}/playlist/{filename?}")
    data class UserPlaylist(val id: Int, val filename: String? = null, val api: UsersApi)

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

    val followingColumn = if (userId == null || userId == uploaderId) {
        Op.nullOp()
    } else {
        countWithFilter(userFilter and followerFilter) greater intLiteral(0)
    }

    return Follows
        .slice(userColumn, followerColumn, followingColumn).select {
            userFilter or followerFilter
        }
        .single().let {
            UserFollowData(it[userColumn], it[followerColumn], it[followingColumn])
        }
}

fun Route.userRoute() {
    val usernameRegex = Regex("^[._\\-A-Za-z0-9]{3,50}$")
    post<UsersApi.Username> {
        requireAuthorization { sess ->
            val req = call.receive<AccountDetailReq>()

            if (!usernameRegex.matches(req.textContent)) {
                call.respond(ActionResponse(false, listOf("Username not valid")))
            } else {
                val success = transaction {
                    try {
                        User.update({ User.id eq sess.userId and (User.uniqueName.isNull() or User.renamedAt.lessEq(DateMinusDays(NowExpression(User.renamedAt.columnType), 1))) }) { u ->
                            u[uniqueName] = req.textContent
                            u[renamedAt] = Expression.build { case().When(uniqueName eq req.textContent, renamedAt).Else(NowExpression(renamedAt.columnType)) }
                        }
                    } catch (e: ExposedSQLException) {
                        -1
                    }
                }

                if (success > 0) {
                    // Success
                    call.sessions.set(sess.copy(uniqueName = req.textContent))
                    call.respond(ActionResponse(true, listOf()))
                } else if (success == 0) {
                    call.respond(ActionResponse(false, listOf("You can only set a new username once per day")))
                } else {
                    call.respond(ActionResponse(false, listOf("Username already taken")))
                }
            }
        }
    }

    post<UsersApi.Description> {
        requireAuthorization { sess ->
            val req = call.receive<AccountDetailReq>()

            val success = transaction {
                try {
                    User.update({ User.id eq sess.userId }) { u ->
                        u[description] = req.textContent.take(500)
                    }
                } catch (e: ExposedSQLException) {
                    0
                }
            }

            if (success > 0) {
                // Success
                call.respond(ActionResponse(true, listOf()))
            } else {
                call.respond(ActionResponse(false, listOf("Something went wrong")))
            }
        }
    }

    post<UsersApi.Admin> {
        requireAuthorization { sess ->
            if (!sess.isAdmin()) {
                ActionResponse(false, listOf("Not an admin"))
            } else {
                val req = call.receive<UserAdminRequest>()
                val allowedUploadSizes = arrayOf(0, 15, 30)
                if (allowedUploadSizes.contains(req.maxUploadSize)) {
                    transaction {
                        fun runUpdate() =
                            User.update({
                                User.id eq req.userId
                            }) { u ->
                                u[uploadLimit] = req.maxUploadSize
                                u[curator] = req.curator
                                u[verifiedMapper] = req.verifiedMapper
                            } > 0

                        runUpdate().also {
                            if (it) {
                                ModLog.insert(
                                    sess.userId,
                                    null,
                                    UploadLimitData(req.maxUploadSize, req.curator, req.verifiedMapper),
                                    req.userId
                                )
                            }
                        }
                    }.let { success ->
                        if (success) {
                            ActionResponse(true, listOf())
                        } else {
                            ActionResponse(false, listOf("User not found"))
                        }
                    }
                } else {
                    ActionResponse(false, listOf("Upload size not allowed"))
                }
            }.let {
                call.respond(it)
            }
        }
    }

    post<UsersApi.Suspend> {
        requireAuthorization { sess ->
            if (!sess.isAdmin()) {
                ActionResponse(false, listOf("Not an admin"))
            } else {
                val req = call.receive<UserSuspendRequest>()
                transaction {
                    fun runUpdate() =
                        User.update({
                            User.id eq req.userId
                        }) { u ->
                            if (req.suspended) u[suspendedAt] = NowExpression(suspendedAt.columnType)
                            else u[suspendedAt] = null
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
                        ActionResponse(true, listOf())
                    } else {
                        ActionResponse(false, listOf("User not found"))
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
            req.captcha,
            {
                if (req.password != req.password2) {
                    ActionResponse(false, listOf("Passwords don't match"))
                } else if (req.password.length < 8) {
                    ActionResponse(false, listOf("Password too short"))
                } else if (!usernameRegex.matches(req.username)) {
                    ActionResponse(false, listOf("Username not valid"))
                } else {
                    try {
                        val bcrypt = String(Bcrypt.hash(req.password, 12))
                        val token = UserCrypto.getHash(req.email)

                        val newUserId = transaction {
                            try {
                                User.insertAndGetId {
                                    it[name] = req.username
                                    it[email] = req.email
                                    it[password] = bcrypt
                                    it[verifyToken] = token
                                    it[uniqueName] = req.username
                                    it[active] = false
                                } to null
                            } catch (e: ExposedSQLException) {
                                if (e.message?.contains("simple_username") == true) {
                                    // Username constraint -> show conflict error
                                    null to ActionResponse(false, listOf("Username taken"))
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
                            sendEmail(
                                req.email,
                                "BeatSaver Account Verification",
                                "${req.username}\n\nTo verify your account, please click the link below:\n${Config.siteBase()}/verify?user=$it&token=$token"
                            )

                            ActionResponse(true)
                        } ?: newUserId.second ?: run {
                            sendEmail(
                                req.email,
                                "BeatSaver Account",
                                "Someone just tried to create a new account at ${Config.siteBase()} with this email address but an account using this email already exists.\n\n" +
                                    "If this wasn't you then you can safely ignore this email otherwise please use a different email"
                            )

                            ActionResponse(true)
                        }
                    } catch (e: IllegalArgumentException) {
                        ActionResponse(false, listOf("Password too long"))
                    }
                }
            }
        ) {
            ActionResponse(false, listOf("Could not verify user [${it.errorCodes.joinToString(", ")}]"))
        }

        response?.let {
            call.respond(it)
        }
    }

    post<UsersApi.Forgot> {
        val req = call.receive<ForgotRequest>()

        val response = requireCaptcha(
            req.captcha,
            {
                transaction {
                    User.select {
                        (User.email eq req.email) and User.password.isNotNull() and (User.active or User.verifyToken.isNotNull())
                    }.firstOrNull()?.let { UserDao.wrapRow(it) }
                }?.let { user ->
                    val builder = Jwts.builder().setExpiration(Date(Clock.System.now().plus(20.toDuration(DurationUnit.MINUTES)).epochSeconds))
                    builder.setSubject(user.id.toString())
                    builder.signWith(UserCrypto.keyForUser(user))
                    val jwt = builder.compact()

                    sendEmail(
                        req.email,
                        "BeatSaver Password Reset",
                        "You can reset your password for the account `${user.uniqueName}` by clicking here: ${Config.siteBase()}/reset/$jwt\n\n" +
                            "If this wasn't you then you can safely ignore this email."
                    )
                }

                ActionResponse(true)
            }
        ) {
            ActionResponse(false, listOf("Could not verify user [${it.errorCodes.joinToString(", ")}]"))
        }

        response?.let {
            call.respond(it)
        }
    }

    get<UsersApi.Sessions> {
        requireAuthorization {
            val oauthSessions = transaction {
                RefreshTokenTable
                    .join(OauthClient, JoinType.INNER, RefreshTokenTable.clientId, OauthClient.clientId)
                    .select {
                        (RefreshTokenTable.userName eq it.userId) and (RefreshTokenTable.expiration greater Clock.System.now().toJavaInstant())
                    }
                    .map { row ->
                        OauthSession(
                            UserCrypto.encrypt(row[RefreshTokenTable.id].value),
                            row[OauthClient.name],
                            row[OauthClient.iconUrl],
                            row[RefreshTokenTable.scope].split(","),
                            row[RefreshTokenTable.expiration].toKotlinInstant()
                        )
                    }
            }

            val sessionId = call.request.cookies[cookieName]
            val siteSessions = if (MongoClient.connected) {
                MongoClient.sessions.find(MongoSession::session / Session::userId eq it.userId).map { row ->
                    SiteSession(
                        UserCrypto.encrypt(row._id),
                        row.session.countryCode,
                        row.expireAt,
                        row._id == sessionId
                    )
                }.toList()
            } else listOf()

            call.respond(SessionsData(oauthSessions, siteSessions))
        }
    }

    delete<UsersApi.Sessions> {
        requireAuthorization {
            val req = call.receive<SessionRevokeRequest>()
            val id = UserCrypto.decrypt(req.id)

            if (!req.site) {
                DBTokenStore.revokeRefreshToken(id)
            } else if (!MongoClient.connected) {
                call.respond(ActionResponse(false, listOf("Can't revoke in memory sessions")))
                return@requireAuthorization
            } else if (id == call.request.cookies[cookieName]) {
                call.respond(ActionResponse(false, listOf("Can't revoke current session")))
                return@requireAuthorization
            } else {
                MongoClient.sessions.deleteOne(
                    MongoSession::_id eq id
                )
            }

            call.respond(ActionResponse(true))
        }
    }

    post<UsersApi.Reset> {
        val req = call.receive<ResetRequest>()

        val response = if (req.password != req.password2) {
            ActionResponse(false, listOf("Passwords don't match"))
        } else if (req.password.length < 8) {
            ActionResponse(false, listOf("Password too short"))
        } else {
            try {
                val bcrypt = String(Bcrypt.hash(req.password, 12))

                val i = req.jwt.lastIndexOf('.')
                val withoutSignature = req.jwt.substring(0, i + 1)
                val untrusted = Jwts.parserBuilder().build().parseClaimsJwt(withoutSignature)

                untrusted.body.subject.toInt().let { userId ->
                    transaction {
                        User.select {
                            User.id eq userId
                        }.firstOrNull()?.let { UserDao.wrapRow(it) }?.let { user ->
                            // If the jwt is valid we can reset the user's password :D
                            try {
                                Jwts.parserBuilder().setSigningKey(UserCrypto.keyForUser(user)).build().parseClaimsJws(req.jwt)

                                val success = User.update({
                                    User.id eq userId
                                }) {
                                    it[password] = bcrypt

                                    // The user must have received an email to reset their password so
                                    // we can also activate their account
                                    it[verifyToken] = null
                                    it[active] = true
                                } > 0

                                if (success) {
                                    DBTokenStore.deleteForUser(userId)

                                    ActionResponse(true)
                                } else {
                                    ActionResponse(false, listOf("Failed to update password"))
                                }
                            } catch (e: SignatureException) {
                                // As previous password is included in key the signature will fail if the password
                                // has changed since we sent the link
                                ActionResponse(false, listOf("Reset token no longer valid"))
                            } catch (e: JwtException) {
                                ActionResponse(false, listOf("Reset token is malformed"))
                            }
                        } ?: ActionResponse(false, listOf("User not found"))
                    }
                }
            } catch (e: IllegalArgumentException) {
                ActionResponse(false, listOf("Password too long"))
            } catch (e: ExpiredJwtException) {
                ActionResponse(false, listOf("Password reset link has expired"))
            } catch (e: JwtException) {
                ActionResponse(false, listOf("Reset token is malformed"))
            }
        }

        call.respond(response)
    }

    post<UsersApi.Me> {
        requireAuthorization { sess ->
            val req = call.receive<AccountRequest>()

            val response = if (req.password == null || req.currentPassword == null) {
                // Not a password reset request
                ActionResponse(true)
            } else if (req.password != req.password2) {
                ActionResponse(false, listOf("Passwords don't match"))
            } else if (req.password.length < 8) {
                ActionResponse(false, listOf("Password too short"))
            } else {
                try {
                    val bcrypt = String(Bcrypt.hash(req.password, 12))

                    transaction {
                        User.select {
                            User.id eq sess.userId
                        }.firstOrNull()?.let { r ->
                            val curPw = r[User.password]
                            if (curPw != null && Bcrypt.verify(req.currentPassword, curPw.toByteArray())) {
                                User.update({
                                    User.id eq sess.userId
                                }) {
                                    it[password] = bcrypt
                                }
                                DBTokenStore.deleteForUser(sess.userId)
                                ActionResponse(true)
                            } else {
                                ActionResponse(false, listOf("Current password incorrect"))
                            }
                        } ?: ActionResponse(false, listOf("Account not found")) // Shouldn't ever happen
                    }
                } catch (e: IllegalArgumentException) {
                    ActionResponse(false, listOf("Password too long"))
                }
            }

            call.respond(response)
        }
    }

    post<UsersApi.Follow> {
        requireAuthorization { user ->
            val req = call.receive<UserFollowRequest>()

            transaction {
                if (req.followed) {
                    Follows.insertIgnore { follow ->
                        follow[userId] = req.userId
                        follow[followerId] = user.userId
                        follow[since] = NowExpression(since.columnType)
                    }.insertedCount
                } else {
                    Follows.deleteWhere {
                        (Follows.userId eq req.userId) and (Follows.followerId eq user.userId)
                    }
                }
            }

            call.respond(HttpStatusCode.OK)
        }
    }

    get<UsersApi.Find> {
        val user = transaction {
            UserDao.wrapRows(
                User.select {
                    User.hash.eq(it.id) and User.active
                }
            ).firstOrNull()
        }

        if (user == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respond(UserDetail.from(user))
        }
    }

    get<UsersApi.List> {
        val us = transaction {
            val userAlias = User.slice(User.upvotes, User.id, User.name, User.uniqueName, User.description, User.avatar, User.hash, User.discordId).select {
                Op.TRUE and User.active
            }.orderBy(User.upvotes, SortOrder.DESC).limit(it.page).alias("u")

            val query = userAlias
                .join(Beatmap, JoinType.INNER, userAlias[User.id], Beatmap.uploader) {
                    Beatmap.deletedAt.isNull()
                }
                .join(Versions, JoinType.INNER, onColumn = Beatmap.id, otherColumn = Versions.mapId, additionalConstraint = { Versions.state eq EMapState.Published })
                .slice(
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
                    countWithFilter(Beatmap.ranked),
                    Beatmap.uploaded.min(),
                    Beatmap.uploaded.max()
                )
                .selectAll()
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
                        it[countWithFilter(Beatmap.ranked)],
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

        call.respond(us)
    }

    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    get<UsersApi.UserPlaylist> {
        val (maps, user) = transaction {
            Beatmap.joinVersions().select {
                Beatmap.uploader eq it.id and Beatmap.deletedAt.isNull()
            }.complexToBeatmap().sortedByDescending { b -> b.uploaded } to User.select { User.id eq it.id and User.active }.firstOrNull()?.let { row -> UserDetail.from(row) }
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
                .slice(
                    Beatmap.id.count(),
                    Beatmap.upVotesInt.sum(),
                    Beatmap.downVotesInt.sum(),
                    Beatmap.bpm.avg(),
                    Beatmap.score.avg(3),
                    Beatmap.duration.avg(0),
                    countWithFilter(Beatmap.ranked),
                    Beatmap.uploaded.min(),
                    Beatmap.uploaded.max()
                ).select {
                    (Beatmap.uploader eq user.id) and (Beatmap.deletedAt.isNull())
                }.first().let {
                    UserStats(
                        it[Beatmap.upVotesInt.sum()] ?: 0,
                        it[Beatmap.downVotesInt.sum()] ?: 0,
                        it[Beatmap.id.count()].toInt(),
                        it[countWithFilter(Beatmap.ranked)],
                        it[Beatmap.bpm.avg()]?.toFloat() ?: 0f,
                        it[Beatmap.score.avg(3)]?.movePointRight(2)?.toFloat() ?: 0f,
                        it[Beatmap.duration.avg(0)]?.toFloat() ?: 0f,
                        it[Beatmap.uploaded.min()]?.toKotlinInstant(),
                        it[Beatmap.uploaded.max()]?.toKotlinInstant()
                    )
                }

        val cases = EDifficulty.values().associateWith { diffCase(it) }
        val diffStats = Difficulty
            .join(Beatmap, JoinType.INNER, Beatmap.id, Difficulty.mapId)
            .join(Versions, JoinType.INNER, Difficulty.versionId, Versions.id) {
                Versions.state eq EMapState.Published
            }
            .slice(Difficulty.id.count(), *cases.values.toTypedArray())
            .select {
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

    get<UsersApi.Me> {
        requireAuthorization {
            val (user, alertCount, followData) = transaction {
                Triple(
                    UserDao.wrapRows(
                        User.select {
                            User.id.eq(it.userId)
                        }
                    ).first(),
                    alertCount(it.userId),
                    followData(it.userId, it.userId)
                )
            }

            call.sessions.set(Session.fromUser(user, alertCount, it.oauth2ClientId, call))

            val dualAccount = user.discordId != null && user.email != null && user.uniqueName != null

            call.respond(
                UserDetail.from(user, stats = statsForUser(user), followData = followData, description = true).let { usr ->
                    if (dualAccount) {
                        usr.copy(type = AccountType.DUAL, email = user.email)
                    } else {
                        usr.copy(email = user.email)
                    }
                }
            )
        }
    }

    fun userBy(where: SqlExpressionBuilder.() -> Op<Boolean>) =
        UserDao.wrapRows(User.select(where)).firstOrNull() ?: throw NotFoundException()

    options<MapsApi.UserId> {
        call.response.header("Access-Control-Allow-Origin", "*")
        call.respond(HttpStatusCode.OK)
    }
    get<MapsApi.UserId>("Get user info".responds(ok<UserDetail>(), notFound())) {
        call.response.header("Access-Control-Allow-Origin", "*")
        val (user, followData) = transaction {
            userBy {
                (User.id eq it.id) and User.active
            } to followData(it.id, call.sessions.get<Session>()?.userId)
        }

        val userDetail = UserDetail.from(user, stats = statsForUser(user), followData = followData, description = true).let {
            if (call.sessions.get<Session>()?.isAdmin() == true) {
                it.copy(uploadLimit = user.uploadLimit)
            } else {
                it
            }
        }

        call.respond(userDetail)
    }

    options<MapsApi.UserName> {
        call.response.header("Access-Control-Allow-Origin", "*")
        call.respond(HttpStatusCode.OK)
    }
    get<MapsApi.UserName>("Get user info by name".responds(ok<UserDetail>(), notFound())) {
        call.response.header("Access-Control-Allow-Origin", "*")
        val user = transaction {
            userBy {
                (User.uniqueName eq it.name) and User.active
            }
        }

        call.respond(UserDetail.from(user, stats = statsForUser(user), description = true))
    }

    fun getFollowerData(page: Long, joinOn: Column<EntityID<Int>>, condition: SqlExpressionBuilder.() -> Op<Boolean>) = transaction {
        val followsSubquery = Follows
            .slice(joinOn, Follows.since)
            .select(condition)
            .limit(page)
            .orderBy(Follows.since, SortOrder.DESC)
            .alias("fs")

        followsSubquery
            .join(User, JoinType.LEFT, followsSubquery[joinOn], User.id)
            .join(Beatmap, JoinType.LEFT, User.id, Beatmap.uploader) {
                Beatmap.deletedAt.isNull()
            }
            .join(Versions, JoinType.LEFT, onColumn = Beatmap.id, otherColumn = Versions.mapId, additionalConstraint = { Versions.state eq EMapState.Published })
            .slice(
                User.id, User.name, User.uniqueName, User.avatar, User.discordId, User.hash,
                Versions.mapId.count(), User.admin, User.curator, User.verifiedMapper, User.suspendedAt
            )
            .selectAll()
            .groupBy(User.id, followsSubquery[Follows.since])
            .orderBy(followsSubquery[Follows.since], SortOrder.DESC)
            .map { row ->
                UserDetail.from(UserDao.wrapRow(row), stats = UserStats(totalMaps = row[Versions.mapId.count()].toInt()))
            }
    }

    get<UsersApi.Following> {
        val users = getFollowerData(it.page, Follows.followerId) {
            Follows.userId eq it.user
        }

        call.respond(users)
    }

    get<UsersApi.FollowedBy> {
        requireAuthorization { sess ->
            if (it.user != sess.userId) call.respond(HttpStatusCode.Forbidden)

            val users = getFollowerData(it.page, Follows.userId) {
                Follows.followerId eq it.user
            }

            call.respond(users)
        }
    }
}

fun diffCase(diff: EDifficulty) = Sum(Expression.build { case().When(Difficulty.difficulty eq diff, intLiteral(1)).Else(intLiteral(0)) }, IntegerColumnType())
