package io.beatmaps.api

import io.beatmaps.common.json
import io.beatmaps.login.patreon.PatreonBase
import io.beatmaps.login.patreon.PatreonIncluded
import io.beatmaps.login.patreon.PatreonMembership
import io.beatmaps.login.patreon.PatreonObject
import io.beatmaps.login.patreon.PatreonResponse
import io.beatmaps.login.patreon.PatreonStatus
import io.beatmaps.login.patreon.PatreonTier
import io.beatmaps.login.patreon.PatreonUser
import io.ktor.client.request.get
import io.ktor.client.statement.request
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days

class LinkPatreonTest : ApiTestBase() {
    @Test
    fun linkAccount() = testApplication {
        externalServices {
            hosts("https://patreon.com") {
                this@hosts.install(ContentNegotiation) {
                    json()
                }
                this@hosts.routing {
                    post("api/oauth2/token") {
                        call.respond("""
                            {
                                "access_token": "1",
                                "refresh_token": "2",
                                "token_type": "bearer",
                                "expires_in": 3600,
                                "scope": "identity"
                            }
                        """.trimIndent())
                    }
                    get("api/oauth2/v2/identity") {
                        call.respond(PatreonResponse(
                            JsonObject(emptyMap()),
                            listOf(
                                json.encodeToJsonElement(
                                    PatreonIncluded(
                                        "1234", // TODO: Randomise
                                        PatreonUser.fieldKey,
                                        fixture<PatreonUser>()
                                    )
                                ),
                                json.encodeToJsonElement(
                                    PatreonIncluded(
                                        fixture<String>(),
                                        PatreonMembership.fieldKey,
                                        PatreonMembership(
                                            currentlyEntitledAmountCents = 100,
                                            patronStatus = PatreonStatus.ACTIVE,
                                            nextChargeDate = Clock.System.now().plus(7.days)
                                        )
                                    )
                                ),
                                json.encodeToJsonElement(
                                    PatreonIncluded(
                                        fixture<String>(),
                                        PatreonTier.fieldKey,
                                        fixture<PatreonTier>()
                                    )
                                )
                            ),
                            JsonObject(emptyMap())
                        ))
                    }
                }
            }
        }

        val client = setup()

        val uid = transaction {
            createUser().first
        }

        login(client, uid)

        val response = client.get("/patreon?code=1&state=2")
        assertEquals(HttpStatusCode.OK, response.status, "Request should be successful")
        assertEquals("/profile#account", response.request.url.fullPath)
    }
}