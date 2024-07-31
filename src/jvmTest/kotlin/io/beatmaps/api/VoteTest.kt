package io.beatmaps.api

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class VoteTest : ApiTestBase() {
    @Test
    fun submitVoteSteamSuccess() = testApplication {
        externalServices {
            hosts("https://api.steampowered.com") {
                this@hosts.install(ContentNegotiation) {
                    json()
                }
                this@hosts.routing {
                    get("ISteamUserAuth/AuthenticateUserTicket/v1") {
                        call.respond(SteamAPIResponse(
                            SteamAPIResponseObject(
                                SteamAPIResponseParams("OK", 76561198000000000L, 76561198000000000L, vacbanned = false, publisherbanned = false)
                            )
                        ))
                    }
                }
            }
        }

        val client = setup()

        val (_, hash) = transaction {
            val (uid, _) = createUser()
            createMap(uid)
        }

        val response = client.post("/api/vote") {
            contentType(ContentType.Application.Json)
            setBody(
                VoteRequest(AuthRequest("76561198000000000", proof = "fake-proof"), hash, true)
            )
        }
        val actionResponse = response.body<ActionResponse>()

        assertEquals(HttpStatusCode.OK, response.status, "Vote should be successful")
        assertEquals(true, actionResponse.success, "Vote should be successful")
        assertContentEquals(listOf(), actionResponse.errors, "Vote should have no errors")
    }

    @Test
    fun submitVoteOculusSuccess() = testApplication {
        externalServices {
            hosts("http://localhost:3030") {
                this@hosts.install(ContentNegotiation) {
                    json()
                }
                this@hosts.routing {
                    post("auth/oculus") {
                        call.respond(OculusAuthResponse(true))
                    }
                }
            }
        }

        val client = setup()

        val (_, hash) = transaction {
            val (uid, _) = createUser()
            createMap(uid)
        }

        val response = client.post("/api/vote") {
            contentType(ContentType.Application.Json)
            setBody(
                VoteRequest(AuthRequest(oculusId = "1234567890", proof = "fake-proof"), hash, true)
            )
        }
        val actionResponse = response.body<ActionResponse>()

        assertEquals(HttpStatusCode.OK, response.status, "Vote should be successful")
        assertEquals(true, actionResponse.success, "Vote should be successful")
        assertContentEquals(listOf(), actionResponse.errors, "Vote should have no errors")
    }

    @Test
    fun submitVoteSteamInvalidProof() = testApplication {
        externalServices {
            hosts("https://api.steampowered.com") {
                this@hosts.install(ContentNegotiation) {
                    json()
                }
                this@hosts.routing {
                    get("ISteamUserAuth/AuthenticateUserTicket/v1") {
                        call.respond(SteamAPIResponse(
                            SteamAPIResponseObject(
                                error = SteamAPIResponseError(123, "Bad stuff")
                            )
                        ))
                    }
                }
            }
        }

        val client = setup()

        val (_, hash) = transaction {
            val (uid, _) = createUser()
            createMap(uid)
        }

        val response = client.post("/api/vote") {
            contentType(ContentType.Application.Json)
            setBody(
                VoteRequest(AuthRequest("76561198000000000", proof = "fake-proof"), hash, true)
            )
        }
        val actionResponse = response.body<ActionResponse>()

        assertEquals(HttpStatusCode.BadRequest, response.status, "Vote should not be successful")
        assertEquals(false, actionResponse.success, "Vote should not be successful")
        assertContentEquals(listOf("Could not validate steam token"), actionResponse.errors)
    }

    @Test
    fun submitVoteOculusInvalidProof() = testApplication {
        externalServices {
            hosts("http://localhost:3030") {
                this@hosts.install(ContentNegotiation) {
                    json()
                }
                this@hosts.routing {
                    post("auth/oculus") {
                        call.respond(OculusAuthResponse(false, "Bad stuff"))
                    }
                }
            }
        }

        val client = setup()

        val (_, hash) = transaction {
            val (uid, _) = createUser()
            createMap(uid)
        }

        val response = client.post("/api/vote") {
            contentType(ContentType.Application.Json)
            setBody(
                VoteRequest(AuthRequest(oculusId = "1234567890", proof = "fake-proof"), hash, true)
            )
        }
        val actionResponse = response.body<ActionResponse>()

        assertEquals(HttpStatusCode.BadRequest, response.status, "Vote should not be successful")
        assertEquals(false, actionResponse.success, "Vote should not be successful")
        assertContentEquals(listOf("Could not validate oculus token"), actionResponse.errors)
    }

    @Test
    fun submitVoteNoUser() = testApplication {
        val client = setup()

        val (_, hash) = transaction {
            val (uid, _) = createUser()
            createMap(uid)
        }

        val response = client.post("/api/vote") {
            contentType(ContentType.Application.Json)
            setBody(
                VoteRequest(AuthRequest(proof = "fake-proof"), hash, true)
            )
        }
        val actionResponse = response.body<ActionResponse>()

        assertEquals(HttpStatusCode.BadRequest, response.status, "Vote should not be successful")
        assertEquals(false, actionResponse.success, "Vote should not be successful")
        assertContentEquals(listOf("No user identifier provided"), actionResponse.errors)
    }
}
