package io.beatmaps.api

import io.beatmaps.common.api.EMapState
import io.beatmaps.common.dbo.Collaboration
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import kotlin.test.Test
import kotlin.test.assertEquals

class PublishAlertsTest : ApiTestBase() {
    data class SetupInfo(
        val mapperId: Int,
        val mapId: Int,
        val hash: String,
        val collaboratorId: Int,
        val followsMapper: Int,
        val followsCollaborator: Int,
        val followsBoth: Int
    )

    @Test
    fun noAlertsWhenNotPublished() = testApplication {
        val client = setup()

        val info = newSuspendedTransaction {
            val (uid, _) = createUser()
            val (cid, _) = createUser()
            val (mid, hash) = createMap(uid, false)

            val (fid, _) = createUser()
            val (fid2, _) = createUser()
            val (fid3, _) = createUser()

            follows(uid, fid)
            follows(cid, fid2)

            follows(uid, fid3)
            follows(cid, fid3)

            SetupInfo(uid, mid, hash, cid, fid, fid2, fid3)
        }

        suspend fun changeMapState(published: Boolean) {
            login(client, info.mapperId)
            val response = client.post("/api/testplay/state") {
                contentType(ContentType.Application.Json)
                setBody(StateUpdate(info.hash, if (published) EMapState.Published else EMapState.Uploaded, info.mapId))
            }

            assertEquals(HttpStatusCode.OK, response.status, "Change map state ($published) should succeed")
        }

        suspend fun addCollaborator() {
            login(client, info.mapperId)
            val collabReqResponse = client.post("/api/collaborations/request") {
                contentType(ContentType.Application.Json)
                setBody(CollaborationRequestData(info.mapId, info.collaboratorId))
            }

            assertEquals(HttpStatusCode.OK, collabReqResponse.status, "Collab request should succeed")

            val collabId = newSuspendedTransaction {
                Collaboration.select(Collaboration.id).where {
                    Collaboration.collaboratorId eq info.collaboratorId
                }.single()[Collaboration.id].value
            }

            login(client, info.collaboratorId)
            val collabResponse = client.post("/api/collaborations/response") {
                contentType(ContentType.Application.Json)
                setBody(CollaborationResponseData(collabId, true))
            }

            assertEquals(HttpStatusCode.OK, collabResponse.status, "Collab accept should succeed")
        }

        suspend fun removeCollaborator() {
            login(client, info.mapperId)
            val collabReqResponse = client.post("/api/collaborations/remove") {
                contentType(ContentType.Application.Json)
                setBody(CollaborationRemoveData(info.mapId, info.collaboratorId))
            }

            assertEquals(HttpStatusCode.OK, collabReqResponse.status, "Collab remove should succeed")
        }

        addCollaborator()

        checkAlertCount(client, info.followsMapper, 0, "No mapper alert when unpublished")
        checkAlertCount(client, info.followsCollaborator, 0, "No collaborator alert when unpublished")
        checkAlertCount(client, info.followsBoth, 0, "No alerts when unpublished")

        changeMapState(true)

        checkAlertCount(client, info.followsMapper, 1, "Single mapper alert when published")
        checkAlertCount(client, info.followsCollaborator, 1, "Single collaborator alert when published")
        checkAlertCount(client, info.followsBoth, 1, "Single alert when published even when following mapper and collaborator")

        removeCollaborator()
        addCollaborator()

        checkAlertCount(client, info.followsMapper, 0, "No mapper alert for collab when published")
        checkAlertCount(client, info.followsCollaborator, 1, "Single collaborator alert for new collab when published")
        checkAlertCount(client, info.followsBoth, 0, "No alert for new collab when published when following mapper and collaborator")

        changeMapState(false)

        removeCollaborator()
        addCollaborator()

        checkAlertCount(client, info.followsMapper, 0, "No mapper alert when unpublished (prev published)")
        checkAlertCount(client, info.followsCollaborator, 0, "No collaborator alert when unpublished (prev published)")
        checkAlertCount(client, info.followsBoth, 0, "No alerts when unpublished  (prev published)")
    }
}
