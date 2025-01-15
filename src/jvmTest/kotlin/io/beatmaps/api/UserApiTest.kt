package io.beatmaps.api

import io.beatmaps.common.Config
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
