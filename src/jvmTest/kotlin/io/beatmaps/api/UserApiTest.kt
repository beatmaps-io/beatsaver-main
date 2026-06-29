package io.beatmaps.api

import io.beatmaps.common.Config
import io.beatmaps.common.ModLogOpType
import io.beatmaps.common.SilenceData
import io.beatmaps.common.dbo.ModLog
import io.beatmaps.common.dbo.ReviewSilence
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class UserApiTest : ApiTestBase() {
    @Test
    fun me() = testApplication {
        val client = setup()

        val (userId, username) = transaction {
            createUser()
        }

        login(client, userId)

        val response = client.get("/api/users/me")
        val userDetail = response.body<UserDetail>()

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            UserDetail(
                userId,
                username,
                "",
                avatar = "https://beatsaver.com/static/logo.svg",
                stats = UserStats(diffStats = UserDiffStats(0, 0, 0, 0, 0, 0)),
                followData = UserFollowData(0, 0, following = false, upload = false, curation = false, collab = false),
                type = AccountType.SIMPLE,
                email = "$username@beatsaver.com",
                admin = false,
                curator = false,
                seniorCurator = false,
                playlistUrl = "${Config.apiBase(true)}/users/id/$userId/playlist",
                blurnsfw = true
            ),
            userDetail
        )

        client.get("/logout")

        val responseLogout = client.get("/api/users/me")

        assertEquals(HttpStatusCode.Unauthorized, responseLogout.status)

        val responseById = client.get("/api/users/id/$userId")
        val userDetailById = responseById.body<UserDetail>()

        assertEquals(HttpStatusCode.OK, responseById.status)
        assertEquals(
            UserDetail(
                userId,
                username,
                "",
                avatar = "https://beatsaver.com/static/logo.svg",
                stats = UserStats(diffStats = UserDiffStats(0, 0, 0, 0, 0, 0)),
                followData = UserFollowData(0, null, following = false, upload = false, curation = false, collab = false),
                type = AccountType.SIMPLE,
                admin = false,
                curator = false,
                seniorCurator = false,
                playlistUrl = "${Config.apiBase(true)}/users/id/$userId/playlist"
            ),
            userDetailById
        )
    }

    @Test
    fun accountStandingCombinesSuspensionsAndSilences() = testApplication {
        val client = setup()
        val (userId, _) = createUser()

        login(client, 2)
        client.post("/api/users/suspend") {
            contentType(ContentType.Application.Json)
            setBody(UserSuspendRequest(userId, true, "spam"))
        }
        client.post("/api/users/silence") {
            contentType(ContentType.Application.Json)
            setBody(UserReviewSilenceRequest(userId, 80, "inappropriate beatmap comment"))
        }

        val detail = client.get("/api/users/id/$userId").body<UserDetail>()
        val silence = detail.accountStanding.single { it.action == AccountStandingAction.SILENCE }
        val suspension = detail.accountStanding.single { it.action == AccountStandingAction.SUSPENSION }

        assertEquals(true, detail.reviewSilenced)
        assertNotNull(detail.reviewSilencedUntil)
        assertEquals(80, silence.lengthMinutes)
        assertEquals("inappropriate beatmap comment", silence.description)
        assertEquals(AccountStandingStatus.ACTIVE, silence.status)
        assertEquals(null, suspension.lengthMinutes)
        assertEquals("spam", suspension.description)
        assertEquals(AccountStandingStatus.ACTIVE, suspension.status)
    }

    @Test
    fun silenceAppearsInModLog() = testApplication {
        val client = setup()
        val (userId, _) = createUser()

        login(client, 2)
        client.post("/api/users/silence") {
            contentType(ContentType.Application.Json)
            setBody(UserReviewSilenceRequest(userId, 80, "inappropriate beatmap comment"))
        }

        val allEntries = client.get("/api/modlog/0").body<List<ModLogEntry>>()
        val silenceEntries = client.get("/api/modlog/0?type=Silence").body<List<ModLogEntry>>()
        val silence = silenceEntries.first { it.user.id == userId }
        val action = silence.action as SilenceData

        assertTrue(allEntries.any { it.user.id == userId && it.type == ModLogOpType.Silence })
        assertEquals(ModLogOpType.Silence, silence.type)
        assertEquals("Silence", silence.actionLabel)
        assertEquals(true, action.silenced)
        assertEquals(80, action.durationMinutes)
        assertEquals("inappropriate beatmap comment", action.reason)
    }

    @Test
    fun revokedSilenceAppearsInModLogAndAccountStanding() = testApplication {
        val client = setup()
        val (userId, _) = createUser()

        login(client, 2)
        client.post("/api/users/silence") {
            contentType(ContentType.Application.Json)
            setBody(UserReviewSilenceRequest(userId, 80, "inappropriate beatmap comment"))
        }
        client.post("/api/users/silence") {
            contentType(ContentType.Application.Json)
            setBody(UserReviewSilenceRequest(userId, 0, null))
        }

        val silenceEntries = client.get("/api/modlog/0?type=Silence").body<List<ModLogEntry>>()
        val revoke = silenceEntries.first { it.user.id == userId && (it.action as SilenceData).silenced == false }

        login(client, userId)
        val detail = client.get("/api/users/id/$userId").body<UserDetail>()
        val silence = detail.accountStanding.single { it.action == AccountStandingAction.SILENCE }

        assertEquals(ModLogOpType.Silence, revoke.type)
        assertEquals("Silence revoked", revoke.actionLabel)
        assertEquals(false, detail.reviewSilenced)
        assertEquals(null, detail.reviewSilencedUntil)
        assertEquals(80, silence.lengthMinutes)
        assertEquals("inappropriate beatmap comment", silence.description)
        assertEquals(AccountStandingStatus.REVOKED, silence.status)
    }

    @Test
    fun expiredSilenceStaysInPublicAccountStanding() = testApplication {
        val client = setup()
        val (userId, _) = createUser()

        login(client, 2)
        client.post("/api/users/silence") {
            contentType(ContentType.Application.Json)
            setBody(UserReviewSilenceRequest(userId, 80, "inappropriate beatmap comment"))
        }
        transaction {
            val expired = Clock.System.now().minus(1.days).toJavaInstant()
            ReviewSilence.update({ ReviewSilence.userId eq userId }) {
                it[createdAt] = expired
                it[silencedUntil] = expired.plusSeconds(80 * 60)
            }
        }

        login(client, userId)
        val detail = client.get("/api/users/id/$userId").body<UserDetail>()
        val silence = detail.accountStanding.single { it.action == AccountStandingAction.SILENCE }

        assertEquals(false, detail.reviewSilenced)
        assertEquals(AccountStandingStatus.EXPIRED, silence.status)
        assertEquals("inappropriate beatmap comment", silence.description)
    }

    @Test
    fun permanentSilenceShowsWithoutExpiry() = testApplication {
        val client = setup()
        val (userId, _) = createUser()

        login(client, 2)
        client.post("/api/users/silence") {
            contentType(ContentType.Application.Json)
            setBody(UserReviewSilenceRequest(userId, null, "review spam"))
        }
        transaction {
            ReviewSilence.update({ ReviewSilence.userId eq userId }) {
                it[createdAt] = Clock.System.now().minus(100.days).toJavaInstant()
            }
        }

        login(client, userId)
        val detail = client.get("/api/users/id/$userId").body<UserDetail>()
        val silence = detail.accountStanding.single { it.action == AccountStandingAction.SILENCE }

        assertEquals(true, detail.reviewSilenced)
        assertEquals(null, detail.reviewSilencedUntil)
        assertEquals(null, silence.lengthMinutes)
        assertEquals("review spam", silence.description)
        assertEquals(AccountStandingStatus.ACTIVE, silence.status)
    }

    @Test
    fun publicStandingHidesOldInactiveActions() = testApplication {
        val client = setup()
        val (userId, _) = createUser()

        login(client, 2)
        client.post("/api/users/suspend") {
            contentType(ContentType.Application.Json)
            setBody(UserSuspendRequest(userId, true, "spam"))
        }
        client.post("/api/users/suspend") {
            contentType(ContentType.Application.Json)
            setBody(UserSuspendRequest(userId, false, null))
        }
        client.post("/api/users/silence") {
            contentType(ContentType.Application.Json)
            setBody(UserReviewSilenceRequest(userId, 80, "inappropriate beatmap comment"))
        }

        transaction {
            val old = Clock.System.now().minus(100.days).toJavaInstant()
            ReviewSilence.update({ ReviewSilence.userId eq userId }) {
                it[createdAt] = old
                it[silencedUntil] = old.plusSeconds(80 * 60)
            }
            ModLog.update({ (ModLog.targetUser eq userId) and (ModLog.type eq ModLogOpType.Suspend.ordinal) }) {
                it[opAt] = old
            }
        }

        login(client, userId)
        val publicDetail = client.get("/api/users/id/$userId").body<UserDetail>()

        login(client, 2)
        val adminDetail = client.get("/api/users/id/$userId").body<UserDetail>()
        val adminSilence = adminDetail.accountStanding.single { it.action == AccountStandingAction.SILENCE }
        val adminSuspension = adminDetail.accountStanding.single { it.action == AccountStandingAction.SUSPENSION }

        assertEquals(emptyList(), publicDetail.accountStanding)
        assertEquals(setOf(AccountStandingAction.SILENCE, AccountStandingAction.SUSPENSION), adminDetail.accountStanding.map { it.action }.toSet())
        assertEquals(AccountStandingStatus.EXPIRED, adminSilence.status)
        assertEquals(AccountStandingStatus.REVOKED, adminSuspension.status)
    }
}
