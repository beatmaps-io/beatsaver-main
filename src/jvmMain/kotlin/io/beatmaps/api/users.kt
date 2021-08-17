package io.beatmaps.api

import com.toxicbakery.bcrypt.Bcrypt
import de.nielsfalk.ktor.swagger.get
import de.nielsfalk.ktor.swagger.notFound
import de.nielsfalk.ktor.swagger.ok
import de.nielsfalk.ktor.swagger.responds
import io.beatmaps.common.Config
import io.beatmaps.common.ModLogOpType
import io.beatmaps.common.api.EDifficulty
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.client
import io.beatmaps.common.db.countWithFilter
import io.beatmaps.common.db.updateReturning
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Difficulty
import io.beatmaps.common.dbo.ModLog
import io.beatmaps.common.dbo.ModLogDao
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.UserDao
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.common.pub
import io.beatmaps.common.sendEmail
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.MalformedJwtException
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.SignatureException
import io.ktor.application.call
import io.ktor.client.features.timeout
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.locations.options
import io.ktor.locations.post
import io.ktor.request.receive
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.sessions.sessions
import io.ktor.sessions.set
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.OrOp
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Sum
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.avg
import org.jetbrains.exposed.sql.booleanLiteral
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.intLiteral
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.min
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.math.BigInteger
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Date

fun UserDetail.Companion.from(other: UserDao, roles: Boolean = false, stats: UserStats? = null) =
    UserDetail(other.id.value, other.uniqueName ?: other.name, other.uniqueName != null, other.hash, if (roles) other.testplay else null,
        other.avatar ?: "https://www.gravatar.com/avatar/${other.hash}?d=retro", stats, if (other.discordId != null) AccountType.DISCORD else AccountType.SIMPLE)

fun UserDetail.Companion.from(row: ResultRow, roles: Boolean = false) = from(UserDao.wrapRow(row), roles)
fun Alert.Companion.from(other: ModLogDao, map: MapDetail) = Alert(map, other.opAt.toKotlinInstant(), other.realAction())
fun Alert.Companion.from(row: ResultRow) = from(ModLogDao.wrapRow(row), MapDetail.from(row))

@Location("/api/users")
class UsersApi {
    @Location("/me")
    data class Me(val api: UsersApi)

    @Location("/username")
    data class Username(val api: UsersApi)

    @Location("/register")
    data class Register(val api: UsersApi)

    @Location("/forgot")
    data class Forgot(val api: UsersApi)

    @Location("/reset")
    data class Reset(val api: UsersApi)

    @Location("/id/{id}/stats")
    data class UserStats(val id: Int, val api: UsersApi)

    @Location("/id/{id}/playlist")
    data class UserPlaylist(val id: Int, val api: UsersApi)

    @Location("/find/{id}")
    data class Find(val id: String, val api: UsersApi)

    @Location("/beatsaver")
    data class LinkBeatsaver(val api: UsersApi)

    @Location("/alerts")
    data class Alerts(val api: UsersApi)

    @Location("/list/{page}")
    data class List(val page: Long = 0, val api: UsersApi)
}

fun Route.userRoute() {
    get<UsersApi.LinkBeatsaver> {
        requireAuthorization {
            call.respond(BeatsaverLink(!it.canLink))
        }
    }

    post<UsersApi.LinkBeatsaver> { _ ->
        requireAuthorization { s ->
            val r = call.receive<BeatsaverLinkReq>()

            val userToCheck = transaction {
                UserDao.wrapRows(User.select {
                    (User.name eq r.user.lowercase() and User.hash.isNotNull()) or (User.hash eq r.user.lowercase() and User.discordId.isNull())
                }).firstOrNull()
            }

            val valid = userToCheck != null && Bcrypt.verify(r.password, userToCheck.password.toByteArray())
            val result = transaction {
                val user = UserDao.wrapRows(User.select {
                    User.id eq s.userId
                }).toList().firstOrNull()

                val canLink = (user?.discordId != null) && (user.hash == null)

                if (!canLink) {
                    call.sessions.set(user?.hash?.let {
                        s.copy(hash = it, canLink = false)
                    } ?: s.copy(canLink = false))
                    return@transaction true to listOf()
                }

                if (valid && userToCheck != null) {
                    val oldmapIds = User.updateReturning({ User.hash eq userToCheck.hash and User.discordId.isNull() }, { u ->
                        u[hash] = null
                        u[uniqueName] = null
                        u[active] = false
                    }, User.id)?.let { r ->
                        if (r.isEmpty()) return@let listOf()

                        // If we returned a row
                        val oldId = r.first()[User.id]

                        Beatmap
                            .slice(Beatmap.id)
                            .select {
                                Beatmap.uploader eq oldId
                            }.map { it[Beatmap.id].value }
                            .also {
                                Beatmap.update({ Beatmap.uploader eq oldId }) {
                                    it[uploader] = s.userId
                                }
                            }
                    }

                    User.update({ User.id eq s.userId }) {
                        it[hash] = userToCheck.hash
                        it[admin] = OrOp(listOf(admin, booleanLiteral(userToCheck.admin)))
                        it[upvotes] = userToCheck.upvotes
                        if (r.useOldName) {
                            it[uniqueName] = userToCheck.uniqueName
                        }
                    }
                    call.sessions.set(s.copy(hash = userToCheck.hash))

                    true to oldmapIds
                } else {
                    false to listOf()
                }
            }

            result.second?.forEach { updatedMapId ->
                call.pub("beatmaps", "maps.$updatedMapId.updated", null, updatedMapId)
            }

            call.respond(BeatsaverLink(result.first))
        }
    }

    val usernameRegex = Regex("^[._\\-A-Za-z0-9]{3,}$")
    post<UsersApi.Username> {
        requireAuthorization { sess ->
            val req = call.receive<UsernameReq>()

            if (!usernameRegex.matches(req.username)) {
                call.respond(ActionResponse(false, listOf("Username not valid")))
            } else {
                val success = transaction {
                    try {
                        User.update({ User.id eq sess.userId and User.uniqueName.isNull() }) { u ->
                            u[uniqueName] = req.username
                        }
                        true
                    } catch (e: ExposedSQLException) {
                        false
                    }
                }

                if (success) {
                    // Success
                    call.sessions.set(sess.copy(uniqueName = req.username))
                    call.respond(ActionResponse(true, listOf()))
                } else {
                    call.respond(ActionResponse(false, listOf("Username already taken")))
                }
            }
        }
    }

    val secret = System.getenv("BSHASH_SECRET")?.let { Base64.getDecoder().decode(it) } ?: byteArrayOf()
    fun getHash(userId: String) = MessageDigest.getInstance("SHA1").let {
        it.update(secret + userId.toByteArray())
        String.format("%040x", BigInteger(1, it.digest()))
    }
    fun keyForUser(user: UserDao) = Keys.hmacShaKeyFor(secret + "${user.password}-${user.createdAt}".toByteArray())

    post<UsersApi.Register> {
        val req = call.receive<RegisterRequest>()

        val response = requireCaptcha(req.captcha, {
            if (req.password != req.password2) {
                ActionResponse(false, listOf("Passwords don't match"))
            } else if (req.password.length < 8) {
                ActionResponse(false, listOf("Password too short"))
            } else {
                val bcrypt = String(Bcrypt.hash(req.password, 12))
                val token = getHash(req.email)

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
                        "To verify your account, please click the link below:\n${Config.basename}/verify?user=${newUserId}&token=${token}"
                    )

                    ActionResponse(true)
                } ?: newUserId.second ?: run {
                    sendEmail(
                        req.email,
                        "BeatSaver Account",
                        "Someone just tried to create a new account at ${Config.basename} with this email address but an account using this email already exists.\n\n" +
                                "If this wasn't you then you can safely ignore this email otherwise please use a different email"
                    )

                    ActionResponse(true)
                }
            }
        }) {
            ActionResponse(false, listOf("Could not verify user [${it.errorCodes.joinToString(", ")}]"))
        }

        response?.let {
            call.respond(it)
        }
    }

    post<UsersApi.Forgot> {
        val req = call.receive<ForgotRequest>()

        val response = requireCaptcha(req.captcha, {
            transaction {
                User.select {
                    (User.email eq req.email) and User.discordId.isNull() and User.password.isNotNull()
                }.firstOrNull()?.let { UserDao.wrapRow(it) }
            }?.let { user ->
                val builder = Jwts.builder().setExpiration(Date.from(Instant.now().plus(20L, ChronoUnit.MINUTES)))
                builder.setSubject(user.id.toString())
                builder.signWith(keyForUser(user))
                val jwt = builder.compact()

                sendEmail(
                    req.email,
                    "BeatSaver Password Reset",
                    "You can reset your password by clicking here: ${Config.basename}/reset/$jwt\n\n" +
                            "If this wasn't you then you can safely ignore this email."
                )
            }

            ActionResponse(true)
        }) {
            ActionResponse(false, listOf("Could not verify user [${it.errorCodes.joinToString(", ")}]"))
        }

        response?.let {
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
            val bcrypt = String(Bcrypt.hash(req.password, 12))

            try {
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
                                Jwts.parserBuilder().setSigningKey(keyForUser(user)).build().parseClaimsJws(req.jwt)

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
                val bcrypt = String(Bcrypt.hash(req.password, 12))

                transaction {
                    User.select {
                        User.id eq sess.userId
                    }.firstOrNull()?.let { r ->
                        if (Bcrypt.verify(req.currentPassword, r[User.password].toByteArray())) {
                            User.update({
                                User.id eq sess.userId
                            }) {
                                it[password] = bcrypt
                            }
                            ActionResponse(true)
                        } else {
                            ActionResponse(false, listOf("Current password incorrect"))
                        }
                    } ?: ActionResponse(false, listOf("Account not found")) // Shouldn't ever happen
                }
            }

            call.respond(response)
        }
    }

    get<UsersApi.Find> {
        val user = transaction {
            UserDao.wrapRows(User.select {
                User.hash.eq(it.id)
            }).firstOrNull()
        }

        if (user == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respond(UserDetail.from(user))
        }
    }

    get<UsersApi.Alerts> {
        requireAuthorization { user ->
            val alerts = transaction {
                ModLog.join(Beatmap, JoinType.INNER, Beatmap.id, ModLog.opOn).select {
                    (Beatmap.uploader eq user.userId) and
                    (ModLog.type inList listOf(ModLogOpType.Unpublish, ModLogOpType.Delete).map { it.ordinal })
                }.orderBy(ModLog.opAt, SortOrder.DESC).limit(30).map { Alert.from(it) }
            }

            call.respond(alerts)
        }
    }

    get<UsersApi.List> {
        val us = transaction {
            val userAlias = User.slice(User.upvotes, User.id, User.name, User.uniqueName, User.avatar, User.hash, User.discordId).select {
                Op.TRUE and User.active
            }.orderBy(User.upvotes, SortOrder.DESC).limit(it.page).alias("u")

            val query = userAlias
                .join(Beatmap, JoinType.INNER, userAlias[User.id], Beatmap.uploader) {
                    Beatmap.deletedAt.isNull()
                }
                .slice(
                    Beatmap.uploader,
                    Beatmap.id.count(),
                    userAlias[User.upvotes],
                    userAlias[User.name],
                    userAlias[User.uniqueName],
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
                .groupBy(Beatmap.uploader, userAlias[User.upvotes], userAlias[User.name], userAlias[User.uniqueName], userAlias[User.avatar], userAlias[User.hash], userAlias[User.discordId])
                .orderBy(userAlias[User.upvotes], SortOrder.DESC)

            query.toList().map {
                val uniqueName = it[userAlias[User.uniqueName]]
                UserDetail(
                    it[Beatmap.uploader].value,
                    uniqueName ?: it[userAlias[User.name]],
                    uniqueName != null,
                    avatar = it[userAlias[User.avatar]] ?: "https://www.gravatar.com/avatar/${it[userAlias[User.hash]]}?d=retro",
                    stats = UserStats(
                        it[userAlias[User.upvotes]],
                        it[Beatmap.downVotesInt.sum()] ?: 0,
                        it[Beatmap.id.count()].toInt(),
                        it[countWithFilter(Beatmap.ranked)],
                        it[Beatmap.bpm.avg()]?.toFloat() ?: 0f,
                        it[Beatmap.score.avg(3)]?.movePointRight(2)?.toFloat() ?: 0f,
                        it[Beatmap.duration.avg(0)]?.toFloat() ?: 0f,
                        it[Beatmap.uploaded.min()]?.toKotlinInstant(),
                        it[Beatmap.uploaded.max()]?.toKotlinInstant()
                    ),
                    type = if (it[userAlias[User.discordId]] != null) AccountType.DISCORD else AccountType.SIMPLE
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
            }.complexToBeatmap().sortedByDescending { b -> b.uploaded } to UserDetail.from(User.select { User.id eq it.id }.first())
        }

        val playlistSongs = maps.mapNotNull { map ->
            map.versions.values.firstOrNull { v -> v.state == EMapState.Published }?.let { v ->
                PlaylistSong(
                    v.key64,
                    v.hash,
                    map.name
                )
            }
        }

        val imageStr = Base64.getEncoder().encodeToString(
            client.get<ByteArray>(user.avatar) {
                timeout {
                    socketTimeoutMillis = 30000
                    requestTimeoutMillis = 60000
                }
            }
        )

        val dateStr = formatter.format(LocalDateTime.now())

        call.response.headers.append(HttpHeaders.ContentDisposition, "filename=\"${user.name}-$dateStr.bplist\"")
        call.respond(
            Playlist(
                "Maps by ${user.name} (${playlistSongs.size} Total)",
                user.name,
                "All maps by ${user.name} ($dateStr)",
                imageStr,
                PlaylistCustomData("${Config.apiremotebase}/users/id/${it.id}/playlist"),
                playlistSongs
            )
        )
    }

    fun statsForUser(user: UserDao) = transaction {
        val statTmp =
            Beatmap.slice(
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

        val cases = EDifficulty.values().associate { it to diffCase(it) }
        val diffStats = Difficulty
            .join(Beatmap, JoinType.INNER, Beatmap.id, Difficulty.mapId)
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
            val user = transaction {
                UserDao.wrapRows(User.select {
                    User.id.eq(it.userId)
                }).first()
            }

            if (user.uniqueName != null && it.uniqueName == null) {
                call.sessions.set(it.copy(uniqueName = user.uniqueName))
            }

            call.respond(UserDetail.from(user, stats = statsForUser(user)))
        }
    }

    options<MapsApi.UserId> {
        call.response.header("Access-Control-Allow-Origin", "*")
        call.respond(HttpStatusCode.OK)
    }
    get<MapsApi.UserId>("Get user info".responds(ok<UserDetail>()).responds(notFound())) {
        call.response.header("Access-Control-Allow-Origin", "*")
        val user = transaction {
            UserDao.wrapRows(User.select {
                User.id.eq(it.id)
            }).first()
        }

        call.respond(UserDetail.from(user, stats = statsForUser(user)))
    }
}

fun diffCase(diff: EDifficulty) = Sum(Expression.build { case().When(Difficulty.difficulty eq diff, intLiteral(1)).Else(intLiteral(0)) }, IntegerColumnType())