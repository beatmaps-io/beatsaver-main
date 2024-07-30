package io.beatmaps.api

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.lang.Integer.toHexString
import kotlin.test.Test
import kotlin.test.assertEquals

class ReviewApiTest : ApiTestBase() {
    @Test
    fun fullPageReturnedForUser() = testApplication {
        val client = setup()

        val userId = newSuspendedTransaction {
            val (mapOwner, _) = createUser()
            val (userId, _) = createUser()

            val maps = List(25) {
                createMap(mapOwner).first.let { mapId ->
                    mapId to review(userId, mapId)
                }
            }

            reviewReply(maps[0].second, mapOwner)
            reviewReply(maps[0].second, mapOwner)

            userId
        }

        val reviewsResponse = client.get("/api/review/user/$userId/0")
        val body = reviewsResponse.body<ReviewsResponse>()

        assertEquals(HttpStatusCode.OK, reviewsResponse.status, "User reviews request should succeed")
        assertEquals(20, body.docs.size)

        val reviewsResponsePage2 = client.get("/api/review/user/$userId/1")
        val bodyPage2 = reviewsResponsePage2.body<ReviewsResponse>()

        assertEquals(HttpStatusCode.OK, reviewsResponsePage2.status, "User reviews request page 2 should succeed")
        assertEquals(5, bodyPage2.docs.size)
    }

    @Test
    fun fullPageReturnedForMap() = testApplication {
        val client = setup()

        val mapId = newSuspendedTransaction {
            val (mapOwner, _) = createUser()
            val (mapId, _) = createMap(mapOwner)
            val reviewIds = List(25) {
                review(createUser().first, mapId)
            }

            reviewReply(reviewIds[0], mapOwner)
            reviewReply(reviewIds[0], mapOwner)

            mapId
        }

        val reviewsResponse = client.get("/api/review/map/${toHexString(mapId)}/0")
        val body = reviewsResponse.body<ReviewsResponse>()

        assertEquals(HttpStatusCode.OK, reviewsResponse.status, "Map reviews request should succeed")
        assertEquals(20, body.docs.size)

        val reviewsResponsePage2 = client.get("/api/review/map/${toHexString(mapId)}/1")
        val bodyPage2 = reviewsResponsePage2.body<ReviewsResponse>()

        assertEquals(HttpStatusCode.OK, reviewsResponsePage2.status, "Map reviews request page 2 should succeed")
        assertEquals(5, bodyPage2.docs.size)
    }
}
