package io.beatmaps.api

import io.beatmaps.common.dbo.User
import io.ktor.client.request.get
import io.ktor.client.statement.request
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import kotlin.test.assertEquals

class LinkSteamTest : ApiTestBase() {
    @Test
    fun linkAccount() = testApplication {
        externalServices {
            hosts("https://steamcommunity.com") {
                this@hosts.install(ContentNegotiation) {
                    json()
                }
                this@hosts.routing {
                    post("openid/login") {
                        call.respond("""
                            ns:http://specs.openid.net/auth/2.0
                            is_valid:true
                        """.trimIndent())
                    }
                }
            }
        }

        val client = setup()

        val uid = transaction {
            createUser().first
        }

        login(client, uid)

        val steamId = 76561198000070000 + fixture<Short>()
        val response = client.get("/steam?openid.claimed_id=https%3A%2F%2Fsteamcommunity.com%2Fopenid%2Fid%2F$steamId")
        assertEquals(HttpStatusCode.OK, response.status, "Request should be successful")
        assertEquals("/profile", response.request.url.fullPath)
        assertEquals("", response.request.url.fragment)

        val actualSteamId = transaction {
            User.select(User.steamId).where { User.id eq uid }.single()[User.steamId]
        }
        assertEquals(steamId, actualSteamId)
    }
}