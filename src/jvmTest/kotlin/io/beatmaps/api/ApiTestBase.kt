package io.beatmaps.api

import io.beatmaps.beatmapsio
import io.beatmaps.browser.util.FixtureHelpers
import io.beatmaps.common.db.setupDB
import io.beatmaps.login.Session
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.routing.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.testing.ApplicationTestBuilder

open class ApiTestBase : FixtureHelpers() {
    protected suspend fun ApplicationTestBuilder.setup(): HttpClient {
        setupDB(app = "BeatSaver Tests")

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
            install(HttpCookies)
        }

        application {
            beatmapsio(client)
        }

        routing {
            get("/login-test/{id?}") {
                val id = call.parameters["id"]?.toIntOrNull() ?: 1
                call.sessions.set(Session(id, userEmail = "test@example.com", userName = "test", uniqueName = "test"))
            }
        }

        client.get("/login-test")

        return client
    }

    suspend fun login(client: HttpClient, id: Int? = null) {
        if (id == null) {
            client.get("/login-test")
        } else {
            client.get("/login-test/$id")
        }
    }
}
