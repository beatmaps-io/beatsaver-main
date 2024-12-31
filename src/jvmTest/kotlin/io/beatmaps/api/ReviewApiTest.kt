package io.beatmaps.api

import io.beatmaps.common.api.ReviewSentiment
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.lang.Integer.toHexString
import kotlin.test.Test
import kotlin.test.assertEquals

class ReviewApiTest : ApiTestBase() {
    @Test
    fun badId() = testApplication {
        val client = setup()

        val reviewsResponse = client.get("/api/review/map/z/0")

        assertEquals(HttpStatusCode.NotFound, reviewsResponse.status, "Reviews request should fail")
    }

    @Test
    fun noReviews() = testApplication {
        val client = setup()

        val mapId = newSuspendedTransaction {
            val (mapOwner, _) = createUser()
            val (mapId, _) = createMap(mapOwner)

            mapId
        }

        val reviewsResponse = client.get("/api/review/map/$mapId/0")
        val body = reviewsResponse.body<ReviewsResponse>()

        assertEquals(HttpStatusCode.OK, reviewsResponse.status, "Reviews request should succeed")
        assertEquals(0, body.docs.size)
    }

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

    private val fakeReview = PutReview("Example text", ReviewSentiment.POSITIVE, "fake-captcha")
    private val fakeReply = ReplyRequest("Fake reply", "fake-captcha")

    @Test
    fun suspendedCantReview() = testApplication {
        val client = setup()

        val (mapId, otherUserId) = newSuspendedTransaction {
            val (mapOwner, _) = createUser()
            val (otherUser, _) = createUser(suspended = true)
            val (mapId, _) = createMap(mapOwner)

            toHexString(mapId) to otherUser
        }

        login(client, otherUserId)
        val createCollaboratorReview = client.put("/api/review/single/$mapId/$otherUserId") {
            contentType(ContentType.Application.Json)
            setBody(fakeReview)
        }

        assertEquals(HttpStatusCode.BadRequest, createCollaboratorReview.status, "Reviews should not be allowed by suspended users")
    }

    @Test
    fun uploaderCantReview() = testApplication {
        val client = setup()

        val (mapId, mapOwner, otherUserId) = newSuspendedTransaction {
            val (mapOwner, _) = createUser()
            val (otherUser, _) = createUser()
            val (mapId, _) = createMap(mapOwner, collaborators = listOf(otherUser))

            Triple(toHexString(mapId), mapOwner, otherUser)
        }

        login(client, mapOwner)
        val createReview = client.put("/api/review/single/$mapId/$mapOwner") {
            contentType(ContentType.Application.Json)
            setBody(fakeReview)
        }

        assertEquals(HttpStatusCode.BadRequest, createReview.status, "Reviews should not be allowed by uploader")

        login(client, otherUserId)
        val createCollaboratorReview = client.put("/api/review/single/$mapId/$otherUserId") {
            contentType(ContentType.Application.Json)
            setBody(fakeReview)
        }

        assertEquals(HttpStatusCode.BadRequest, createCollaboratorReview.status, "Reviews should not be allowed by collaborators")
    }

    @Test
    fun alertsForReviews() = testApplication {
        val client = setup()

        val (mapId, mapOwner, otherUserId) = newSuspendedTransaction {
            val (mapOwner, _) = createUser()
            val (otherUser, _) = createUser()
            val (mapId, _) = createMap(mapOwner)

            Triple(toHexString(mapId), mapOwner, otherUser)
        }

        login(client, otherUserId)
        val createReview = client.put("/api/review/single/$mapId/$otherUserId") {
            contentType(ContentType.Application.Json)
            setBody(fakeReview)
        }

        assertEquals(HttpStatusCode.OK, createReview.status, "New review request should succeed")
        checkAlertCount(client, mapOwner, 1)
    }

    @Test
    fun suspendedCantReply() = testApplication {
        val client = setup()

        val (reviewId, userIds) = newSuspendedTransaction {
            val (mapOwner, _) = createUser(suspended = true)
            val (collabUser, _) = createUser(suspended = true)
            val (otherUser, _) = createUser(suspended = true)
            val (mapId, _) = createMap(mapOwner, collaborators = listOf(collabUser))
            val reviewId = review(otherUser, mapId)

            reviewId to listOf(mapOwner, collabUser, otherUser)
        }

        userIds.forEach { userId ->
            login(client, userId)
            val createCollaboratorReview = client.post("/api/reply/create/$reviewId") {
                contentType(ContentType.Application.Json)
                setBody(fakeReply)
            }

            assertEquals(HttpStatusCode.BadRequest, createCollaboratorReview.status, "Replies should not be allowed by suspended users")
        }
    }

    @Test
    fun correctUsersCanReply() = testApplication {
        val client = setup()

        val (reviewId, userIds, notAllowedUser) = newSuspendedTransaction {
            val (mapOwner, _) = createUser()
            val (collabUser, _) = createUser()
            val (otherUser, _) = createUser()
            val (notAllowedUser, _) = createUser()
            val (mapId, _) = createMap(mapOwner, collaborators = listOf(collabUser))
            val reviewId = review(otherUser, mapId)

            Triple(reviewId, listOf(mapOwner, collabUser, otherUser), notAllowedUser)
        }

        userIds.forEach { userId ->
            login(client, userId)
            val createCollaboratorReview = client.post("/api/reply/create/$reviewId") {
                contentType(ContentType.Application.Json)
                setBody(fakeReply)
            }

            assertEquals(HttpStatusCode.OK, createCollaboratorReview.status, "Replies should be allowed by uploader, collaborators and original reviewer")
        }
        val (mapOwner, collabUser, otherUser) = userIds
        checkAlertCount(client, mapOwner, 2)
        checkAlertCount(client, collabUser, 2)
        checkAlertCount(client, otherUser, 2)

        login(client, notAllowedUser)
        val createCollaboratorReview = client.post("/api/reply/create/$reviewId") {
            contentType(ContentType.Application.Json)
            setBody(fakeReply)
        }

        assertEquals(HttpStatusCode.BadRequest, createCollaboratorReview.status, "Replies should not be allowed by other users")
    }
}
