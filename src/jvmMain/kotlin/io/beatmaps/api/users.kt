package io.beatmaps.api

import com.toxicbakery.bcrypt.Bcrypt
import de.nielsfalk.ktor.swagger.get
import de.nielsfalk.ktor.swagger.notFound
import de.nielsfalk.ktor.swagger.ok
import de.nielsfalk.ktor.swagger.responds
import io.beatmaps.common.Config
import io.beatmaps.common.EmailChangedData
import io.beatmaps.common.PasswordChangedData
import io.beatmaps.common.RevokeSessionsData
import io.beatmaps.common.SuspendData
import io.beatmaps.common.UploadLimitData
import io.beatmaps.common.api.EAlertType
import io.beatmaps.common.api.EDifficulty
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.api.EPlaylistType
import io.beatmaps.common.client
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
import io.beatmaps.common.dbo.joinVersions
import io.beatmaps.common.sendEmail
import io.beatmaps.login.MongoClient
import io.beatmaps.login.MongoSession
import io.beatmaps.login.Session
import io.beatmaps.login.cookieName
import io.beatmaps.login.server.DBTokenStore
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
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.SignatureException
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
import io.ktor.util.pipeline.PipelineContext
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
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.intLiteral
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.min
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
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
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

fun md5(input: String) =
    MessageDigest.getInstance("MD5").let { md ->
        BigInteger(1, md.digest(input.toByteArray())).toString(16).padStart(32, '0')
    }

fun JwtBuilder.setExpiration(duration: Duration): JwtBuilder = setExpiration(Date.from(Clock.System.now().plus(duration).toJavaInstant()))
fun parseJwtUntrusted(jwt: String): Jwt<Header<*>, Claims> =
    jwt.substring(0, jwt.lastIndexOf('.') + 1).let {
        Jwts.parserBuilder().build().parseClaimsJwt(it)
    }

fun UserDetail.Companion.getAvatar(other: UserDao) = other.avatar ?: "https://www.gravatar.com/avatar/${other.hash ?: md5(other.uniqueName ?: other.name)}?d=retro"

fun PatreonDao?.toTier() = if (this != null) {
    if (expireAt?.let { it.toKotlinInstant() >= Clock.System.now() } == true) {
        PatreonTier.fromPledge(pledge ?: 0)
    } else {
        PatreonTier.None
    }
} else {
    null
}

fun UserDetail.Companion.from(other: UserDao, roles: Boolean = false, stats: UserStats? = null, followData: UserFollowData? = null, description: Boolean = false, patreon: Boolean = false) =
    UserDetail(
        other.id.value, other.uniqueName ?: other.name, if (description) other.description else null, other.uniqueName != null, other.hash, if (roles) other.testplay else null,
        getAvatar(other), stats, followData, if (other.discordId != null) AccountType.DISCORD else AccountType.SIMPLE,
        admin = other.admin, curator = other.curator, seniorCurator = other.seniorCurator, curatorTab = other.curatorTab, verifiedMapper = other.verifiedMapper, suspendedAt = other.suspendedAt?.toKotlinInstant(),
        playlistUrl = "${Config.apiBase(true)}/users/id/${other.id.value}/playlist", patreon = if (patreon) other.patreon.toTier() else null
    )

fun UserDetail.Companion.from(row: ResultRow, roles: Boolean = false, stats: UserStats? = null, followData: UserFollowData? = null, description: Boolean = false, patreon: Boolean = false) =
    from(UserDao.wrapRow(row), roles, stats, followData, description, patreon)
actual object UserDetailHelper {
    actual fun profileLink(userDetail: UserDetail, tab: String?, absolute: Boolean) = Config.siteBase(absolute) + "/profile/${userDetail.id}" + (tab?.let { "#$it" } ?: "")
}

object UserCrypto {
    private const val defaultSecretEncoded = "ZsEgU9mLHT1Vg+K5HKzlKna20mFQi26ZbB92zILrklNxV5Yxg8SyEcHVWzkspEiCCGkRB89claAWbFhglykfUA=="
    private val secret = Base64.getDecoder().decode(System.getenv("BSHASH_SECRET") ?: defaultSecretEncoded)
    private val sessionSecret = Base64.getDecoder().decode(System.getenv("SESSION_ENCRYPT_SECRET") ?: defaultSecretEncoded)
    private val secretEncryptKey = SecretKeySpec(sessionSecret, "AES")
    private val ephemeralIv = ByteArray(16).apply { SecureRandom().nextBytes(this) }
    private val envIv = System.getenv("BSIV")?.let { Base64.getDecoder().decode(it) } ?: ephemeralIv

    fun keyForUser(user: UserDao) = key(user.password ?: "")

    fun key(pwdHash: String = ""): java.security.Key =
        Keys.hmacShaKeyFor(secret + pwdHash.toByteArray())

    private fun encryptDecrypt(mode: Int, input: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
        cipher.init(mode, secretEncryptKey, IvParameterSpec(iv))
        return cipher.doFinal(input)
    }

    fun encrypt(input: String, iv: ByteArray = envIv) = hex(encryptDecrypt(Cipher.ENCRYPT_MODE, input.toByteArray(), iv))
    fun decrypt(input: String, iv: ByteArray = envIv) = String(encryptDecrypt(Cipher.DECRYPT_MODE, hex(input), iv))

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

    @Location("/search")
    data class Search(val api: UsersApi, val q: String? = "")

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

fun Route.userRoute() {
    val usernameRegex = Regex("^[._\\-A-Za-z0-9]{3,50}$")
    post<UsersApi.Username> {
        requireAuthorization { _, sess ->
            val req = call.receive<AccountDetailReq>()

            if (!usernameRegex.matches(req.textContent)) {
                call.respond(ActionResponse(false, listOf("Username not valid")))
            } else {
                val success = transaction {
                    try {
                        User.update({ User.id eq sess.userId and (User.uniqueName.isNull() or User.renamedAt.lessEq(DateMinusDays(NowExpression(User.renamedAt), 1))) }) { u ->
                            u[uniqueName] = req.textContent
                            u[renamedAt] = Expression.build { case().When(uniqueName eq req.textContent, renamedAt).Else(NowExpression(renamedAt)) }
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
        requireAuthorization { _, sess ->
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
        requireAuthorization { _, sess ->
            if (!sess.isAdmin()) {
                ActionResponse(false, listOf("Not an admin"))
            } else {
                val req = call.receive<UserAdminRequest>()
                if (UserAdminRequest.allowedUploadSizes.contains(req.maxUploadSize)) {
                    transaction {
                        fun runUpdate() =
                            User.update({
                                User.id eq req.userId
                            }) { u ->
                                u[uploadLimit] = req.maxUploadSize
                                u[curator] = req.curator
                                u[seniorCurator] = req.curator && req.seniorCurator
                                u[verifiedMapper] = req.verifiedMapper
                                u[curatorTab] = req.curatorTab
                            } > 0

                        runUpdate().also {
                            if (it) {
                                ModLog.insert(
                                    sess.userId,
                                    null,
                                    UploadLimitData(req.maxUploadSize, req.curator, req.verifiedMapper, req.curatorTab),
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
        requireAuthorization { _, sess ->
            if (!sess.isAdmin()) {
                ActionResponse(false, listOf("Not an admin"))
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
                            val jwt = Jwts.builder()
                                .setExpiration(30.toDuration(DurationUnit.DAYS))
                                .setSubject(it.value.toString())
                                .claim("action", "register")
                                .signWith(UserCrypto.key())
                                .compact()

                            sendEmail(
                                req.email,
                                "BeatSaver Account Verification",
                                "${req.username}\n\nTo verify your account, please click the link below:\n${Config.siteBase()}/verify/$jwt"
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

        call.respond(response)
    }

    post<UsersApi.Forgot> {
        val req = call.receive<ForgotRequest>()

        val response = requireCaptcha(
            req.captcha,
            {
                transaction {
                    User.selectAll().where {
                        (User.email eq req.email) and User.password.isNotNull() and (User.active or User.verifyToken.isNotNull())
                    }.firstOrNull()?.let { UserDao.wrapRow(it) }
                }?.let { user ->
                    val jwt = Jwts.builder()
                        .setExpiration(20.toDuration(DurationUnit.MINUTES))
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

                ActionResponse(true)
            }
        ) {
            ActionResponse(false, listOf("Could not verify user [${it.errorCodes.joinToString(", ")}]"))
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
                            UserCrypto.encrypt(row._id),
                            row.session.countryCode,
                            row.expireAt,
                            row._id == sessionId
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
                ActionResponse(false, listOf("Not an admin or no reason given"))
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
                            and(MongoSession::_id ne sessionId, MongoSession::session / Session::userId eq userId)
                        )
                    }
                }

                ActionResponse(true)
            }

            call.respond(response)
        }
    }

    delete<UsersApi.SessionsById> { byId ->
        requireAuthorization { _, sess ->
            val req = call.receive<SessionRevokeRequest>()
            val userId = req.userId ?: sess.userId
            val id = UserCrypto.decrypt(byId.id)

            val response = if (userId != sess.userId && !sess.isAdmin()) {
                ActionResponse(false, listOf("Not an admin"))
            } else if (req.site == null) {
                ActionResponse(false, listOf("site property is required when deleting by id"))
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

                    if (!req.site) {
                        DBTokenStore.revokeRefreshToken(id)
                        ActionResponse(true)
                    } else if (!MongoClient.connected) {
                        ActionResponse(false, listOf("Can't revoke in memory sessions"))
                    } else if (id == call.request.cookies[cookieName]) {
                        ActionResponse(false, listOf("Can't revoke current session"))
                    } else {
                        MongoClient.sessions.deleteOne(
                            MongoSession::_id eq id
                        )
                        ActionResponse(true)
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
                req.captcha,
                {
                    newSuspendedTransaction {
                        User.selectAll().where {
                            (User.id eq sess.userId)
                        }.firstOrNull()?.let { UserDao.wrapRow(it) }
                    }?.let { user ->
                        if (user.emailChangedAt.toKotlinInstant() > Clock.System.now().minus(10.toDuration(DurationUnit.DAYS))) {
                            ActionResponse(false, listOf("You can only change email once every 10 days"))
                        } else {
                            val jwt = Jwts.builder()
                                .setExpiration(20.toDuration(DurationUnit.MINUTES))
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

                            ActionResponse(true)
                        }
                    } ?: ActionResponse(false, listOf("User not found"))
                }
            ) {
                ActionResponse(false, it.errorCodes.map { "Captcha error: $it" })
            }

            call.respond(response)
        }
    }

    fun PipelineContext<*, ApplicationCall>.sendReclaimMail(user: UserDao) =
        user.email?.let {
            val jwt = Jwts.builder()
                .setExpiration(7.toDuration(DurationUnit.DAYS))
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
                            ActionResponse(true)
                        } else if (!isReclaim && user.emailChangedAt.toKotlinInstant() > Clock.System.now().minus(10.toDuration(DurationUnit.DAYS))) {
                            ActionResponse(false, listOf("You can only change email once every 10 days"))
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
                            } > 0

                            if (success) {
                                UserLog.insert(userId, null, EmailChangedData(user.email, newEmail))

                                // Log out user as sessions contain their email so they are now invalid
                                DBTokenStore.deleteForUser(userId)
                                MongoClient.deleteSessionsFor(userId)

                                if (!isReclaim) {
                                    sendReclaimMail(user)
                                }

                                ActionResponse(true)
                            } else {
                                ActionResponse(false, listOf("Failed to update email"))
                            }
                        } else {
                            ActionResponse(false, listOf("Current password incorrect"))
                        }
                    } catch (e: ExposedSQLException) {
                        ActionResponse(false, listOf("Email in use on another account"))
                    } catch (e: SignatureException) {
                        ActionResponse(false, listOf("Token no longer valid"))
                    } catch (e: JwtException) {
                        ActionResponse(false, listOf("Bad token"))
                    }
                } ?: ActionResponse(false, listOf("User not found"))
            }
        } catch (e: IllegalArgumentException) {
            ActionResponse(false, listOf("Password too long"))
        } catch (e: ExpiredJwtException) {
            ActionResponse(false, listOf("Link has expired"))
        } catch (e: JwtException) {
            ActionResponse(false, listOf("Token is malformed"))
        }.let {
            call.respond(it)
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
                                } > 0

                                if (success) {
                                    UserLog.insert(userId, null, PasswordChangedData)

                                    // Revoke all logins
                                    DBTokenStore.deleteForUser(userId)
                                    MongoClient.deleteSessionsFor(userId)

                                    ActionResponse(true)
                                } else {
                                    ActionResponse(false, listOf("Failed to update password"))
                                }
                            } catch (e: SignatureException) {
                                // As previous password is included in key the signature will fail if the password
                                // has changed since we sent the link
                                ActionResponse(false, listOf("Reset token no longer valid"))
                            } catch (e: JwtException) {
                                ActionResponse(false, listOf("Bad token"))
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
        requireAuthorization { _, sess ->
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
                        User.selectAll().where {
                            User.id eq sess.userId
                        }.firstOrNull()?.let { r ->
                            if (r[User.password]?.let { curPw -> Bcrypt.verify(req.currentPassword, curPw.toByteArray()) } == true) {
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
        requireAuthorization { _, user ->
            val req = call.receive<UserFollowRequest>()

            if (req.userId == user.userId && req.following) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Can't follow yourself"))
                return@requireAuthorization
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

    get<UsersApi.List> {
        val us = transaction {
            val userAlias = User.select(User.upvotes, User.id, User.name, User.uniqueName, User.description, User.avatar, User.hash, User.discordId).where {
                Op.TRUE and User.active
            }.orderBy(User.upvotes, SortOrder.DESC).limit(it.page).alias("u")

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

        call.respond(us)
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
                    countWithFilter(Beatmap.ranked),
                    Beatmap.uploaded.min(),
                    Beatmap.uploaded.max()
                ).where {
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
                    if (dualAccount) {
                        usr.copy(type = AccountType.DUAL, email = user.email)
                    } else {
                        usr.copy(email = user.email)
                    }
                }
            }

            call.respond(detail)
        }
    }

    options<MapsApi.UserId> {
        call.response.header("Access-Control-Allow-Origin", "*")
        call.respond(HttpStatusCode.OK)
    }
    get<MapsApi.UserId>("Get user info".responds(ok<UserDetail>(), notFound())) {
        call.response.header("Access-Control-Allow-Origin", "*")

        val userDetail = transaction {
            val user = userBy {
                (User.id eq it.id) and User.active
            }
            val followData = followData(user.id.value, call.sessions.get<Session>()?.userId)

            UserDetail.from(user, stats = statsForUser(user), followData = followData, description = true, patreon = true).let {
                if (call.sessions.get<Session>()?.isAdmin() == true) {
                    it.copy(uploadLimit = user.uploadLimit)
                } else {
                    it
                }
            }
        }

        call.respond(userDetail)
    }

    options<MapsApi.UserIds> {
        call.response.header("Access-Control-Allow-Origin", "*")
        call.respond(HttpStatusCode.OK)
    }
    get<MapsApi.UserIds>("Get user info".responds(ok<UserDetail>(), notFound())) {
        call.response.header("Access-Control-Allow-Origin", "*")

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

    options<MapsApi.UserName> {
        call.response.header("Access-Control-Allow-Origin", "*")
        call.respond(HttpStatusCode.OK)
    }
    get<MapsApi.UserName>("Get user info by name".responds(ok<UserDetail>(), notFound())) {
        call.response.header("Access-Control-Allow-Origin", "*")
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
            .join(User, JoinType.LEFT, followsSubquery[joinOn], User.id)
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
        requireAuthorization { _, sess ->
            if (it.user != sess.userId) call.respond(HttpStatusCode.Forbidden)

            val users = getFollowerData(it.page, Follows.userId) {
                Follows.followerId eq it.user
            }

            call.respond(users)
        }
    }

    get<UsersApi.Search> {
        val users = transaction {
            User
                .selectAll()
                .where {
                    User.uniqueName startsWith it.q and User.active
                }
                .orderBy(length(User.uniqueName), SortOrder.ASC)
                .limit(10)
                .map { row ->
                    UserDetail.from(row)
                }
        }

        call.respond(users)
    }

    options<UsersApi.Curators> {
        call.response.header("Access-Control-Allow-Origin", "*")
        call.respond(HttpStatusCode.OK)
    }
    get<UsersApi.Curators> {
        call.response.header("Access-Control-Allow-Origin", "*")
        val users = transaction {
            User
                .selectAll()
                .where { User.curator eq Op.TRUE }
                .orderBy(User.seniorCurator to SortOrder.DESC, User.name to SortOrder.ASC)
                .limit(50)
                .map { row ->
                    UserDetail.from(row, description = true)
                }
        }

        call.respond(users)
    }
}

fun diffCase(diff: EDifficulty) = Sum(Expression.build { case().When(Difficulty.difficulty eq diff, intLiteral(1)).Else(intLiteral(0)) }, IntegerColumnType())
