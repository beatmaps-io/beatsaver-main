package io.beatmaps.api

import com.fasterxml.jackson.module.kotlin.readValue
import de.nielsfalk.ktor.swagger.ok
import de.nielsfalk.ktor.swagger.post
import de.nielsfalk.ktor.swagger.responds
import io.beatmaps.common.DeletedData
import io.beatmaps.common.UnpublishData
import io.beatmaps.common.api.ECharacteristic
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.client
import io.beatmaps.controllers.reCaptchaVerify
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Beatmap.joinUploader
import io.beatmaps.common.dbo.Difficulty
import io.beatmaps.common.dbo.DifficultyDao
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.db.isFalse
import io.beatmaps.common.dbo.Testplay
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.UserDao
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.common.db.updateReturning
import io.beatmaps.common.db.upsert
import io.beatmaps.common.db.wrapAsExpressionNotNull
import io.beatmaps.common.dbo.ModLog
import io.beatmaps.common.jackson
import io.beatmaps.login.Session
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.client.request.*
import io.ktor.client.request.get
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.locations.post
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.coalesce
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import pl.jutupe.ktor_rabbitmq.publish
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import javax.crypto.SecretKey

@Location("/api/testplay") class TestplayApi {
    @Location("/queue/{page?}") data class Queue(val page: Long? = 0, val includePlayed: Boolean = true, val api: TestplayApi)
    @Location("/recent/{page?}") data class Recent(val page: Long? = 0, val api: TestplayApi)
    @Location("/feedback") data class Feedback(val api: TestplayApi)
    @Location("/state") data class State(val api: TestplayApi)
    @Location("/version") data class Version(val api: TestplayApi)
    @Location("/auth") data class Auth(val api: TestplayApi)
    @Location("/check") data class Check(val api: TestplayApi)
    @Location("/mark") data class Mark(val api: TestplayApi)
}

suspend fun <T> PipelineContext<*, ApplicationCall>.requireAuthorization(block: suspend PipelineContext<*, ApplicationCall>.(Session) -> T) =
    call.sessions.get<Session>()?.let {
        block(it)
    } ?: run { call.respond(HttpStatusCode.Unauthorized); null }

suspend fun <T> PipelineContext<*, ApplicationCall>.requireCaptcha(captcha: String, block: suspend PipelineContext<*, ApplicationCall>.() -> T) =
    withContext(Dispatchers.IO) {
        reCaptchaVerify.verify(captcha, call.request.origin.remoteHost)
    }.isSuccess.let {
        block()
    } ?: run { call.respond(HttpStatusCode.BadRequest); null }

suspend fun <T> PipelineContext<*, ApplicationCall>.captchaIfPresent(captcha: String?, block: suspend PipelineContext<*, ApplicationCall>.() -> T) =
    if (captcha != null) {
        this.requireCaptcha(captcha, block)
    } else {
        block()
    }

@Serializable
data class AuthRequest(val steamId: String? = null, val oculusId: String? = null, val proof: String)
data class CheckRequest(val jwt: String)
data class AuthResponse(val jwt: String, val user: UserDetail)
data class MarkRequest(val jwt: String, val hash: String, val markPlayed: Boolean = true, val removeFromQueue: Boolean = false, val addToQueue: Boolean = false)

data class SteamAPIResponse(val response: SteamAPIResponseObject)
data class SteamAPIResponseObject(val params: SteamAPIResponseParams? = null, val error: SteamAPIResponseError? = null)
data class SteamAPIResponseError(val errorcode: Int, val errordesc: String)
data class SteamAPIResponseParams(val result: String, val steamid: Long, val ownersteamid: Long, val vacbanned: Boolean, val publisherbanned: Boolean)

data class OculusAuthResponse(val success: Boolean, val error: String? = null)

const val beatsaberAppid = 620980
val privateKey = System.getenv("JWT_KEY") ?: ""
val key: SecretKey = Keys.hmacShaKeyFor(privateKey.toByteArray())

suspend fun PipelineContext<*, ApplicationCall>.validateJWT(jwt: String, block: suspend PipelineContext<*, ApplicationCall>.(Int) -> Unit) =
    Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(jwt).body.subject.toInt().let {
        this.block(it)
    }

fun getTestplayQueue(userId: Int?, includePlayed: Boolean, page: Long?) = transaction {
    Beatmap
        .joinVersions(true) { Versions.state eq EMapState.Testplay }
        .joinUploader()
        .select {
            Beatmap.id.inSubQuery(
                Versions.let {
                    if (includePlayed || userId == null) {
                        it
                            .slice(Versions.mapId)
                            .select {
                                Versions.state eq EMapState.Testplay
                            }
                    } else {
                        it
                            .join(Testplay, JoinType.LEFT, onColumn = Versions.id, otherColumn = Testplay.versionId,
                                additionalConstraint = { Testplay.userId eq userId })
                            .slice(Versions.mapId)
                            .select {
                                Testplay.id.isNull() and (Versions.state eq EMapState.Testplay)
                            }
                    }
                }
                    .orderBy(Versions.testplayAt to SortOrder.ASC)
                    .limit(page)
            )
        }
        .complexToBeatmap()
        .map {
            MapDetail.from(it)
        }
}

fun getTestplayRecent(userId: Int, page: Long?) = transaction {
    // Maybe a little bit cross-product, but not made worse by testplay info as only info from the current user is included
    // Beatmaps get grouped by complexToBeatmap and clients need to reconstruct the ordering
    Testplay
        .join(Versions, JoinType.INNER, Testplay.versionId, Versions.id)
        .join(Difficulty, JoinType.INNER, Versions.id, Difficulty.versionId)
        .join(Beatmap, JoinType.INNER, Versions.mapId, Beatmap.id)
        .join(User, JoinType.INNER, Beatmap.uploader, User.id)
        .select {
            Testplay.userId eq userId
        }
        .orderBy(
            isFalse(Testplay.feedback eq "") to SortOrder.ASC,
            Testplay.createdAt to SortOrder.DESC
        )
        .limit(page)
        .complexToBeatmap()
        .map {
            MapDetail.from(it)
        }
}

suspend fun validateSteamToken(steamId: String, proof: String): Boolean {
    val clientId = System.getenv("STEAM_APIKEY") ?: ""
    val json = client.get<String>("https://api.steampowered.com/ISteamUserAuth/AuthenticateUserTicket/v1?key=$clientId&appid=$beatsaberAppid&ticket=${proof}")
    val data = jackson.readValue<SteamAPIResponse>(json)
    return !(data.response.params == null || data.response.params.result != "OK" || data.response.params.steamid.toString() != steamId)
}

val authHost = System.getenv("AUTH_HOST") ?: "http://localhost:3030"
suspend fun validateOculusToken(oculusId: String, proof: String) = try {
    val json = client.post<String>("${authHost}/auth/oculus") {
        contentType(ContentType.Application.Json)
        body = AuthRequest(oculusId = oculusId, proof = proof)
    }
    val data = jackson.readValue<OculusAuthResponse>(json)
    data.success
} catch (e: BadRequestException) {
    false
}

fun Route.testplayRoute() {
    post<TestplayApi.Queue> { req ->
        val jwt = call.receive<CheckRequest>()
        validateJWT(jwt.jwt) { userId ->
            call.respond(getTestplayQueue(userId, false, req.page))
        }
    }

    get<TestplayApi.Queue> { req ->
        val sess = call.sessions.get<Session>()
        call.respond(getTestplayQueue(sess?.userId, req.includePlayed, req.page))
    }

    post<TestplayApi.Recent> { req ->
        val jwt = call.receive<CheckRequest>()
        validateJWT(jwt.jwt) { userId ->
            call.respond(getTestplayRecent(userId, req.page))
        }
    }

    get<TestplayApi.Recent> { req ->
        requireAuthorization { sess ->
            call.respond(getTestplayRecent(sess.userId, req.page))
        }
    }

    post<TestplayApi.State> {
        requireAuthorization { sess ->
            val newState = call.receive<StateUpdate>()

            val valid = transaction {
                if (newState.state == EMapState.Published) {
                    val originalState = MapVersion.from(Versions.select {
                        Versions.hash eq newState.hash
                    }.single())

                    (Versions.join(Beatmap, JoinType.INNER, onColumn = Versions.mapId, otherColumn = Beatmap.id).update({
                        (Versions.mapId eq newState.mapId) and (Beatmap.uploader eq sess.userId)
                    }) {
                        val exp = Expression.build {
                            case()
                                .When(Versions.hash eq newState.hash, QueryParameter(EMapState.Published, Versions.state.columnType))
                                .Else(QueryParameter(EMapState.Uploaded, Versions.state.columnType))
                        }

                        it[Versions.state] = exp
                    } > 0).also { valid ->
                        if (!valid) return@also

                        val stats = DifficultyDao.wrapRows(Difficulty.select {
                            Difficulty.mapId eq newState.mapId
                        })

                        // Set published time for sorting, but don't allow gaming the system
                        val urResult = Beatmap.updateReturning({ Beatmap.id eq newState.mapId }, {
                            it[uploaded] = coalesce(uploaded, NowExpression<Instant?>(uploaded.columnType))

                            it[chroma] = stats.any { s -> s.chroma }
                            it[noodle] = stats.any { s -> s.ne }
                            it[minNps] = stats.minByOrNull { s -> s.nps }?.nps ?: BigDecimal.ZERO
                            it[maxNps] = stats.maxByOrNull { s -> s.nps }?.nps ?: BigDecimal.ZERO
                            it[fullSpread] = stats.filter { diff -> diff.characteristic == ECharacteristic.Standard }
                                .map { diff -> diff.difficulty }
                                .distinct().count() == 5

                            // Because of magical typing reasons this can't be one line
                            // They actually call completely different setting functions
                            if (originalState.sageScore != null && originalState.sageScore < 0) {
                                it[automapper] = true
                            } else {
                                it[automapper] = ai
                            }
                        }, Beatmap.uploaded)

                        urResult?.firstOrNull()?.get(Beatmap.uploaded)?.let { uploadTimeLocal ->
                            Difficulty.join(Versions, JoinType.INNER, onColumn = Difficulty.versionId, otherColumn = Versions.id).update({
                                (Versions.mapId eq newState.mapId)
                            }) {
                                it[Difficulty.createdAt] = uploadTimeLocal
                            } > 0
                        }

                        call.publish("beatmaps", "maps.${newState.mapId}.updated", null, newState.mapId)
                    }
                } else {
                    (Versions.join(Beatmap, JoinType.INNER, onColumn = Versions.mapId, otherColumn = Beatmap.id).update({
                        (Versions.hash eq newState.hash) and (Versions.mapId eq newState.mapId).let { q ->
                            if (sess.admin) {
                                q // If current user is admin don't check the user
                            } else {
                                q and (Beatmap.uploader eq sess.userId)
                            }
                        }
                    }) {
                        it[Versions.state] = newState.state
                        if (newState.state == EMapState.Testplay) {
                            it[Versions.testplayAt] = Instant.now()
                        }
                    } > 0).also { rTemp ->
                        if (rTemp && sess.admin && newState.reason?.isEmpty() == false) {
                            ModLog.insert(
                                sess.userId,
                                newState.mapId,
                                UnpublishData(newState.reason)
                            )
                        }
                    }
                }
            }

            call.respond(if (valid) HttpStatusCode.OK else HttpStatusCode.BadRequest)
        }
    }

    post<TestplayApi.Version> {
        requireAuthorization { sess ->
            val update = call.receive<FeedbackUpdate>()

            val valid = transaction {
                Versions.join(Beatmap, JoinType.INNER, onColumn = Versions.mapId, otherColumn = Beatmap.id).update({
                    (Versions.hash eq update.hash) and (Beatmap.uploader eq sess.userId)
                }) {
                    it[Versions.feedback] = update.feedback
                } > 0
            }

            call.respond(if (valid) HttpStatusCode.OK else HttpStatusCode.BadRequest)
        }
    }

    post<TestplayApi.Auth> {
        val auth = call.receive<AuthRequest>()

        val builder = Jwts.builder().setExpiration(Date.from(Instant.now().plus(3L, ChronoUnit.DAYS)))

        fun validateUser(where: SqlExpressionBuilder.()->Op<Boolean>) = transaction {
            val userIdQuery = User
                .select(where).limit(1).toList()

            if (userIdQuery.isEmpty()) error("Could not find registered user")

            UserDetail.from(userIdQuery[0], true)
        }

        auth.steamId?.let { steamId ->
            if (!validateSteamToken(steamId, auth.proof)) {
                error("Could not validate steam token")
            }

            val user = validateUser {
                User.steamId eq steamId.toLong()
            }

            val jwt = builder.setSubject(user.id.toString()).signWith(key).compact()
            call.respond(AuthResponse(jwt, user))
        } ?: auth.oculusId?.let { oculusId ->
            if (!validateOculusToken(oculusId, auth.proof)) {
                error("Could not validate oculus token")
            }

            val user = validateUser {
                User.oculusId eq oculusId.toLong()
            }

            val jwt = builder.setSubject(user.id.toString()).signWith(key).compact()
            call.respond(AuthResponse(jwt, user))
        } ?: error("No user identifier provided")
    }

    data class VerifyResponse(val success: Boolean, val error: String? = null)
    post<MapsApi.Verify, AuthRequest>("Verify user tokens".responds(ok<VerifyResponse>())) { _, auth ->
        call.respond(auth.steamId?.let { steamId ->
            if (!validateSteamToken(steamId, auth.proof)) {
                VerifyResponse(false, "Could not validate steam token")
            } else {
                VerifyResponse(true)
            }
        } ?: auth.oculusId?.let { oculusId ->
            if (!validateOculusToken(oculusId, auth.proof)) {
                VerifyResponse(false, "Could not validate oculus token")
            } else {
                VerifyResponse(true)
            }
        } ?: VerifyResponse(false, "No user identifier provided"))
    }

    post<TestplayApi.Check> {
        val check = call.receive<CheckRequest>()
        validateJWT(check.jwt) { userId ->
            // If we made it here the jwt is valid
            val user = transaction {
                UserDao.wrapRows(User.select {
                    User.id eq userId
                }.limit(1)).firstOrNull()
            }

            if (user == null) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                call.respond(UserDetail.from(user, true))
            }
        }
    }

    post<TestplayApi.Mark> {
        val mark = call.receive<MarkRequest>()
        validateJWT(mark.jwt) { jwtUserId ->
            transaction {
                // Lazily perform this request
                fun canTestPlay() = UserDao.wrapRows(User.select {
                    User.id eq jwtUserId
                }.limit(1)).firstOrNull()?.testplay == true

                val versionIdVal = if ((mark.removeFromQueue || mark.addToQueue) && canTestPlay()) {
                    val urResult = Versions.updateReturning(
                        {
                            Versions.hash eq mark.hash and (Versions.state neq EMapState.Published)
                        },
                        {
                            it[state] = if (mark.removeFromQueue) EMapState.Feedback else EMapState.Testplay
                        },
                        Versions.id
                    )

                    urResult?.firstOrNull()?.get(Versions.id)
                } else {
                    null
                }

                if (mark.markPlayed) {
                    Testplay.insertIgnore { t ->
                        // If we updated the version already we know it's id, otherwise use a subquery
                        if (versionIdVal != null) {
                            t[versionId] = versionIdVal
                        } else {
                            t[versionId] = wrapAsExpressionNotNull<Int>(
                                Versions.slice(Versions.id).select {
                                    Versions.hash eq mark.hash
                                }.limit(1)
                            )
                        }
                        t[userId] = jwtUserId
                    }
                }
            }
            call.respond(HttpStatusCode.OK)
        }
    }

    post<TestplayApi.Feedback> {
        requireAuthorization { sess ->
            val update = call.receive<FeedbackUpdate>()

            captchaIfPresent(update.captcha) {
                val feedbackAtNew = Instant.now()

                transaction {
                    val subQuery = Versions.slice(Versions.id).select { Versions.hash eq update.hash }

                    if (update.captcha == null) {
                        Testplay.update({ (Testplay.versionId eq wrapAsExpressionNotNull<Int>(subQuery)) and (Testplay.userId eq sess.userId) }) { t ->
                            t[feedbackAt] = feedbackAtNew
                            t[feedback] = update.feedback
                        }
                    } else {
                        Testplay.upsert(conflictIndex = Index(listOf(Testplay.versionId, Testplay.userId), true, "user_version_unique")) { t ->
                            t[versionId] = wrapAsExpressionNotNull<Int>(subQuery)
                            t[userId] = sess.userId
                            t[feedbackAt] = feedbackAtNew
                            t[feedback] = update.feedback
                        }
                    }
                }

                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
