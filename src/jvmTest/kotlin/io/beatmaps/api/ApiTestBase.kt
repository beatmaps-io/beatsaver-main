package io.beatmaps.api

import io.beatmaps.beatmapsio
import io.beatmaps.browser.util.FixtureHelpers
import io.beatmaps.common.db.setupDB
import io.beatmaps.common.dbo.UserDao
import io.beatmaps.login.Session
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.routing.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.testing.ApplicationTestBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.assertEquals

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
                val user = transaction { UserDao[id] }
                call.sessions.set(Session(id, userEmail = user.email, userName = user.name, uniqueName = user.uniqueName, suspended = user.suspendedAt != null))
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

    suspend fun checkAlertCount(client: HttpClient, user: Int, count: Int, message: String? = null) {
        login(client, user)
        val alertsResponse = client.get("/api/alerts/unread").body<List<UserAlert>>()
        client.post("/api/alerts/markall") {
            contentType(ContentType.Application.Json)
            setBody(AlertUpdateAll(true))
        }

        assertEquals(count, alertsResponse.size, message)
    }
}
