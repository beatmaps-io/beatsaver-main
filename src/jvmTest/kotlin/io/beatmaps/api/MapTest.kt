package io.beatmaps.api

import io.beatmaps.common.api.AiDeclarationType
import io.beatmaps.common.dbo.Beatmap
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MapTest : ApiTestBase() {
    private fun curationState(mapId: Int) = transaction {
        Beatmap
            .select(Beatmap.curator, Beatmap.curatedAt)
            .where { Beatmap.id eq mapId }
            .map { row -> row[Beatmap.curator]?.value to row[Beatmap.curatedAt] }
            .single()
    }

    @Test
    fun `Can't curate curated map`() = testApplication {
        val client = setup()

        val (mapId, curatorId) = transaction {
            val (uid, _) = createUser()
            val (mid, _) = createMap(uid)
            val (curatorId, _) = createUser(curator = true)

            mid to curatorId
        }

        login(client, curatorId)
        val (firstResult, secondResult) = listOf(1, 2).map {
            client.post("/api/maps/curate") {
                contentType(ContentType.Application.Json)
                setBody(CurateMap(mapId, true))
            }
        }
        assertEquals(HttpStatusCode.OK, firstResult.status, "First curate request should be successful")
        assertEquals(HttpStatusCode.BadRequest, secondResult.status, "Second curate request should fail")
    }

    @Test
    fun `Can't curate maps`() = testApplication {
        val client = setup()

        val (mapId, userIds) = transaction {
            val (uid, _) = createUser()
            val (mid, _) = createMap(uid)
            val (otherId, _) = createUser()

            mid to listOf(uid, otherId)
        }

        userIds.forEach { uid ->
            login(client, uid)

            val response = client.post("/api/maps/curate") {
                contentType(ContentType.Application.Json)
                setBody(CurateMap(mapId, true))
            }
            assertEquals(HttpStatusCode.BadRequest, response.status, "Curate request should fail")

            val curationStateNew = curationState(mapId)
            assertNull(curationStateNew.first)
            assertNull(curationStateNew.second)
        }
    }

    @Test
    fun `Can curate maps`() = testApplication {
        val client = setup()

        val (mapId, alertUserIds, userIds) = transaction {
            val (uid, _) = createUser()
            val (followerId, _) = createUser()
            val (followerId2, _) = createUser()
            val (mid, _) = createMap(uid)
            val (curatorId, _) = createUser(curator = true)
            val (adminId, _) = createUser(admin = true)

            follows(curatorId, followerId)
            follows(adminId, followerId2)
            follows(curatorId, uid)

            Triple(mid, listOf(uid, followerId, followerId2), listOf(curatorId, adminId))
        }

        userIds.forEach { uid ->
            login(client, uid)

            val response = client.post("/api/maps/curate") {
                contentType(ContentType.Application.Json)
                setBody(CurateMap(mapId, true))
            }
            assertEquals(HttpStatusCode.OK, response.status, "Curate request should be successful")

            val curationState = curationState(mapId)
            assertEquals(uid, curationState.first)
            assertNotNull(curationState.second)

            val responseUncurate = client.post("/api/maps/curate") {
                contentType(ContentType.Application.Json)
                setBody(CurateMap(mapId, false, "Fake reason"))
            }
            assertEquals(HttpStatusCode.OK, responseUncurate.status, "Uncurate request should be successful")

            val curationStateNew = curationState(mapId)
            assertNull(curationStateNew.first)
            assertNull(curationStateNew.second)
        }

        val (userId, followerId, followerId2) = alertUserIds
        // Curate, Uncurate per round
        checkAlertCount(client, userId, 1 + (userIds.size * 2), "User should get 2 alerts per round, plus one for following the curator")
        // Followed curation
        checkAlertCount(client, followerId, 1, "Follower should get 1 alert")
        checkAlertCount(client, followerId2, 1, "Follower should get 1 alert")
    }

    @Test
    fun `Can declare as AI`() = testApplication {
        val client = setup()

        val mapPairs = transaction {
            val (uid, _) = createUser()
            val (adminId, _) = createUser(admin = true)

            listOf(uid to AiDeclarationType.Uploader, adminId to AiDeclarationType.Admin).associateWith {
                createMap(uid, ai = AiDeclarationType.SageScore).first
            }
        }

        mapPairs.forEach { (todo, mapId) ->
            val (userId, newAiType) = todo
            login(client, userId)

            val response = client.post("/api/maps/declareai") {
                contentType(ContentType.Application.Json)
                setBody(AiDeclaration(mapId, true))
            }
            assertEquals(HttpStatusCode.OK, response.status, "Ai declaration should be successful")

            val newAi = transaction {
                Beatmap
                    .select(Beatmap.declaredAi)
                    .where { Beatmap.id eq mapId }
                    .map { row -> row[Beatmap.declaredAi] }
                    .single()
            }
            assertEquals(newAiType, newAi)
        }
    }

    @Test
    fun `Can declare as not AI`() = testApplication {
        val client = setup()

        val mapPairs = transaction {
            val (uid, _) = createUser()
            val (adminId, _) = createUser(admin = true)

            listOf(uid to AiDeclarationType.Uploader, adminId to AiDeclarationType.None).associateWith {
                createMap(uid, ai = AiDeclarationType.SageScore).first
            }
        }

        mapPairs.forEach { (todo, mapId) ->
            val (userId, newAiType) = todo
            login(client, userId)

            val response = client.post("/api/maps/declareai") {
                contentType(ContentType.Application.Json)
                setBody(AiDeclaration(mapId, false))
            }
            assertEquals(HttpStatusCode.OK, response.status, "Ai declaration should be successful")

            val newAi = transaction {
                Beatmap
                    .select(Beatmap.declaredAi)
                    .where { Beatmap.id eq mapId }
                    .map { row -> row[Beatmap.declaredAi] }
                    .single()
            }
            assertEquals(newAiType, newAi)
        }
    }

    @Test
    fun `Can't change AI declaration`() = testApplication {
        val client = setup()

        val mapPairs = transaction {
            val (uid, _) = createUser()
            val (otherUser, _) = createUser()
            val (curatorId, _) = createUser(curator = true)

            listOf(otherUser, curatorId).associateWith {
                createMap(uid, ai = AiDeclarationType.SageScore).first to listOf(true, false)
            }
        }

        mapPairs.forEach { (userId, todo) ->
            val (mapId, states) = todo
            login(client, userId)

            states.forEach {
                val response = client.post("/api/maps/declareai") {
                    contentType(ContentType.Application.Json)
                    setBody(AiDeclaration(mapId, it))
                }
                assertEquals(HttpStatusCode.BadRequest, response.status, "Ai declaration change to $it should fail")
            }
        }
    }
}
