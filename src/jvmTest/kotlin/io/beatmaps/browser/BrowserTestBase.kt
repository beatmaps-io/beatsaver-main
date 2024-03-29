package io.beatmaps.browser

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.Route
import io.beatmaps.DbMigrationType
import io.beatmaps.beatmapsio
import io.beatmaps.common.db.setupDB
import io.beatmaps.login.Session
import io.beatmaps.migrateDB
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.routing.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.testing.TestApplication
import io.ktor.test.dispatcher.testSuspend
import io.ktor.util.toMap
import kotlinx.coroutines.runBlocking

abstract class BrowserTestBase {
    private val testHost = "https://dev.beatsaver.com"

    protected fun bmTest(block: suspend BrowserDsl.() -> Unit) = testSuspend {
        val context = browser.newContext(
            Browser.NewContextOptions()
                .setViewportSize(1920, 919)
                .setPermissions(listOf("clipboard-read"))
        )
        val page = context.newPage()
        page.route("$testHost/**", routeViaClient(client))
        block(BrowserDsl(testHost, client, page))
        page.close()
    }

    private fun routeViaClient(client: HttpClient) = { it: Route ->
        val original = it.request().url()
        val newUrl = original.substringAfter(testHost)

        runBlocking {
            val response = when (it.request().method()) {
                "POST" -> client.post(newUrl) {
                    setBody(it.request().postDataBuffer())
                    it.request().headersArray().forEach {
                        header(it.name, it.value)
                    }
                }
                else -> client.get(newUrl) {
                    it.request().headersArray().forEach {
                        header(it.name, it.value)
                    }
                }
            }

            it.fulfill(
                Route.FulfillOptions()
                    .setStatus(response.status.value)
                    .setBodyBytes(response.body())
                    .setHeaders(response.headers.toMap().mapValues { it.value.first() })
            )
        }
    }

    companion object {
        @JvmStatic
        private val playwright by lazy {
            Playwright.create()
        }

        @JvmStatic
        protected val browser: Browser by lazy {
            playwright.chromium().launch(
                BrowserType.LaunchOptions()
                    .setHeadless(headless)
            )
        }

        @JvmStatic
        protected val testApp by lazy {
            TestApplication {
                val ds = setupDB(app = "BeatSaver Tests")
                migrateDB(ds, DbMigrationType.Test)

                application {
                    beatmapsio()
                }

                // Extra routes to support tests
                routing {
                    get("/login-test/{id?}") {
                        val id = call.parameters["id"]?.toIntOrNull() ?: 1
                        call.sessions.set(Session(id, userEmail = "test@example.com", userName = "test", uniqueName = "test"))
                    }
                }
            }
        }

        @JvmStatic
        protected val client by lazy {
            testApp.createClient {
                install(ContentNegotiation) {
                    json()
                }
                install(HttpCookies)
            }
        }

        private val headless = System.getenv("BUILD_NUMBER") != null
    }
}
