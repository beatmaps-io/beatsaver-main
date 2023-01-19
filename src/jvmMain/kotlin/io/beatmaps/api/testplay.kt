package io.beatmaps.api

import ch.compile.recaptcha.model.SiteVerifyResponse
import de.nielsfalk.ktor.swagger.ok
import de.nielsfalk.ktor.swagger.post
import de.nielsfalk.ktor.swagger.responds
import io.beatmaps.common.UnpublishData
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.client
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.db.isFalse
import io.beatmaps.common.db.updateReturning
import io.beatmaps.common.db.upsert
import io.beatmaps.common.db.wrapAsExpressionNotNull
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Difficulty
import io.beatmaps.common.dbo.ModLog
import io.beatmaps.common.dbo.Testplay
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.UserDao
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.common.dbo.joinUploader
import io.beatmaps.common.dbo.joinVersions
import io.beatmaps.common.pub
import io.beatmaps.controllers.reCaptchaVerify
import io.beatmaps.login.DBTokenStore
import io.beatmaps.login.Session
import io.beatmaps.util.cdnPrefix
import io.beatmaps.util.publishVersion
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.contentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.locations.Location
import io.ktor.server.locations.get
import io.ktor.server.locations.post
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.toJavaInstant
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Index
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.logging.Logger

@Location("/api/testplay") class TestplayApi {
    @Location("/queue/{page}") data class Queue(val page: Long = 0, val includePlayed: Boolean = true, val api: TestplayApi)
    @Location("/recent/{page}") data class Recent(val page: Long = 0, val api: TestplayApi)
    @Location("/feedback") data class Feedback(val api: TestplayApi)
    @Location("/state") data class State(val api: TestplayApi)
    @Location("/version") data class Version(val api: TestplayApi)
    @Location("/mark") data class Mark(val api: TestplayApi)
}

private val pipelineLogger = Logger.getLogger("bmio.Pipeline")

suspend fun <T> PipelineContext<*, ApplicationCall>.requireAuthorization(scope: String? = null, block: suspend PipelineContext<*, ApplicationCall>.(Session) -> T) {
    checkOauthHeader(scope)?.let { u ->
        (u.identity?.metadata?.get("object") as? UserDao)?.let { block(Session.fromUser(it)) }?.let { true }
    } ?: run {
        call.sessions.get<Session>()?.let {
            block(it)
        }
    } ?: call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
}

fun PipelineContext<*, ApplicationCall>.checkOauthHeader(scope: String? = null) =
    call.request.parseAuthorizationHeader().let { authHeader ->
        if (authHeader is HttpAuthHeader.Single) {
            val token = DBTokenStore.accessToken(authHeader.blob)

            when (token?.expired()) {
                true -> DBTokenStore.revokeAccessToken(token.accessToken).let { null }
                false -> {
                    if (token.scopes.contains(scope)) {
                        token
                    } else null
                }
                null -> null
            }
        } else null
    }

suspend fun <T> PipelineContext<*, ApplicationCall>.requireCaptcha(captcha: String, block: suspend PipelineContext<*, ApplicationCall>.() -> T, error: (suspend PipelineContext<*, ApplicationCall>.(SiteVerifyResponse) -> T)? = null) =
    if (reCaptchaVerify == null) {
        pipelineLogger.warning("ReCAPTCHA not setup. Allowing request anyway")
        block()
    } else {
        withContext(Dispatchers.IO) {
            reCaptchaVerify.verify(captcha, call.request.origin.remoteHost)
        }.let { result ->
            if (result.isSuccess) {
                block()
            } else {
                error?.let { it(result) } ?: run { call.respond(HttpStatusCode.BadRequest); null }
            }
        }
    }

suspend fun <T> PipelineContext<*, ApplicationCall>.captchaIfPresent(captcha: String?, block: suspend PipelineContext<*, ApplicationCall>.() -> T) =
    if (captcha != null) {
        this.requireCaptcha(captcha, block)
    } else {
        block()
    }

@Serializable
data class AuthRequest(val steamId: String? = null, val oculusId: String? = null, val proof: String)
@Serializable
data class MarkRequest(val hash: String, val markPlayed: Boolean = true, val removeFromQueue: Boolean = false, val addToQueue: Boolean = false)
@Serializable
data class VerifyResponse(val success: Boolean, val error: String? = null)

data class SteamAPIResponse(val response: SteamAPIResponseObject)
data class SteamAPIResponseObject(val params: SteamAPIResponseParams? = null, val error: SteamAPIResponseError? = null)
data class SteamAPIResponseError(val errorcode: Int, val errordesc: String)
data class SteamAPIResponseParams(val result: String, val steamid: Long, val ownersteamid: Long, val vacbanned: Boolean, val publisherbanned: Boolean)

data class OculusAuthResponse(val success: Boolean, val error: String? = null)

const val beatsaberAppid = 620980

fun PipelineContext<*, ApplicationCall>.getTestplayQueue(userId: Int?, includePlayed: Boolean, page: Long?) = transaction {
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
                            .join(
                                Testplay, JoinType.LEFT, onColumn = Versions.id, otherColumn = Testplay.versionId,
                                additionalConstraint = { Testplay.userId eq userId }
                            )
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
            MapDetail.from(it, cdnPrefix())
        }
}

fun PipelineContext<*, ApplicationCall>.getTestplayRecent(userId: Int, page: Long?) = transaction {
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
            MapDetail.from(it, cdnPrefix())
        }
}

suspend fun validateSteamToken(steamId: String, proof: String): Boolean {
    val clientId = System.getenv("STEAM_APIKEY") ?: ""
    val data = client.get("https://api.steampowered.com/ISteamUserAuth/AuthenticateUserTicket/v1?key=$clientId&appid=$beatsaberAppid&ticket=$proof").body<SteamAPIResponse>()
    return !(data.response.params == null || data.response.params.result != "OK" || data.response.params.steamid.toString() != steamId)
}

val authHost = System.getenv("AUTH_HOST") ?: "http://localhost:3030"
suspend fun validateOculusToken(oculusId: String, proof: String) = try {
    val data = client.post("$authHost/auth/oculus") {
        contentType(ContentType.Application.Json)
        setBody(AuthRequest(oculusId = oculusId, proof = proof))
    }.body<OculusAuthResponse>()
    data.success
} catch (e: BadRequestException) {
    false
} catch (e: ClientRequestException) {
    false
}

fun Route.testplayRoute() {
    post<TestplayApi.Queue> { req ->
        requireAuthorization("testplay") { sess ->
            call.respond(getTestplayQueue(sess.userId, false, req.page))
        }
    }

    get<TestplayApi.Queue> { req ->
        val userId = checkOauthHeader("testplay")?.let { u ->
            u.identity?.username?.toIntOrNull()
        } ?: call.sessions.get<Session>()?.userId

        call.respond(getTestplayQueue(userId, req.includePlayed, req.page))
    }

    get<TestplayApi.Recent> { req ->
        requireAuthorization("testplay") { sess ->
            call.respond(getTestplayRecent(sess.userId, req.page))
        }
    }

    post<TestplayApi.State> {
        requireAuthorization { sess ->
            val newState = call.receive<StateUpdate>().let {
                if (it.state == EMapState.Published && it.scheduleAt != null) {
                    it.copy(state = EMapState.Scheduled)
                } else if (it.state == EMapState.Scheduled && it.scheduleAt == null) {
                    it.copy(state = EMapState.Published)
                } else {
                    it
                }
            }

            val valid = transaction {
                if (newState.state == EMapState.Published) {
                    publishVersion(newState.mapId, newState.hash) {
                        it and (Beatmap.uploader eq sess.userId)
                    }
                } else {
                    fun updateState() =
                        Versions.join(Beatmap, JoinType.INNER, onColumn = Versions.mapId, otherColumn = Beatmap.id).update({
                            (Versions.hash eq newState.hash) and (Versions.mapId eq newState.mapId).let { q ->
                                if (sess.isAdmin()) {
                                    q // If current user is admin don't check the user
                                } else {
                                    q and (Beatmap.uploader eq sess.userId)
                                }
                            }
                        }) {
                            it[Versions.state] = newState.state
                            if (newState.state == EMapState.Testplay) {
                                it[Versions.testplayAt] = Instant.now()
                            } else if (newState.state == EMapState.Scheduled) {
                                // Can't be null as newState is set to Published in that case above
                                it[Versions.scheduledAt] = newState.scheduleAt!!.toJavaInstant()
                            }
                        }

                    (updateState() > 0).also { rTemp ->
                        if (rTemp && sess.isAdmin() && newState.reason?.isEmpty() == false) {
                            ModLog.insert(
                                sess.userId,
                                newState.mapId,
                                UnpublishData(newState.reason),
                                wrapAsExpressionNotNull(
                                    Beatmap.slice(Beatmap.uploader).select { Beatmap.id eq newState.mapId }.limit(1)
                                )
                            )
                        }
                    }
                }
            }

            if (valid) call.pub("beatmaps", "maps.${newState.mapId}.updated.state", null, newState.mapId)
            call.respond(if (valid) HttpStatusCode.OK else HttpStatusCode.BadRequest)
        }
    }

    post<TestplayApi.Version> {
        requireAuthorization("testplay") { sess ->
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

    post<MapsApi.Verify, AuthRequest>("Verify user tokens".responds(ok<VerifyResponse>())) { _, auth ->
        call.respond(
            auth.steamId?.let { steamId ->
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
            } ?: VerifyResponse(false, "No user identifier provided")
        )
    }

    post<TestplayApi.Mark> {
        requireAuthorization("testplay") { sess ->
            val mark = call.receive<MarkRequest>()

            transaction {
                val versionIdVal = if ((mark.removeFromQueue || mark.addToQueue) && sess.testplay) {
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
                        t[userId] = sess.userId
                    }
                }
            }
            call.respond(HttpStatusCode.OK)
        }
    }

    post<TestplayApi.Feedback> {
        requireAuthorization("testplay") { sess ->
            val update = call.receive<FeedbackUpdate>()

            captchaIfPresent(update.captcha) {
                transaction {
                    val subQuery = Versions.slice(Versions.id).select { Versions.hash eq update.hash }

                    if (update.captcha == null) {
                        Testplay.update({ (Testplay.versionId eq wrapAsExpressionNotNull(subQuery)) and (Testplay.userId eq sess.userId) }) { t ->
                            t[feedbackAt] = NowExpression(feedbackAt.columnType)
                            t[feedback] = update.feedback
                        }
                    } else {
                        Testplay.upsert(conflictIndex = Index(listOf(Testplay.versionId, Testplay.userId), true, "user_version_unique")) { t ->
                            t[versionId] = wrapAsExpressionNotNull<Int>(subQuery)
                            t[userId] = sess.userId
                            t[feedbackAt] = NowExpression(feedbackAt.columnType)
                            t[feedback] = update.feedback
                        }
                    }
                }

                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
