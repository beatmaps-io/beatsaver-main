package io.beatmaps.browser

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.Route
import io.beatmaps.DbMigrationType
import io.beatmaps.beatmapsio
import io.beatmaps.common.db.setupDB
import io.beatmaps.common.dbo.UserDao
import io.beatmaps.login.Session
import io.beatmaps.migrateDB
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
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
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Rule
import org.junit.rules.TestName

abstract class BrowserTestBase {
    private val testHost = "https://dev.beatsaver.com"

    @get:Rule
    val name = TestName()

    protected fun bmTest(block: suspend BrowserDsl.() -> Unit) = testSuspend {
        val context = browser.newContext(
            Browser.NewContextOptions()
                .setViewportSize(1920, 919)
                .setPermissions(listOf("clipboard-read", "clipboard-write"))
        )
        val page = context.newPage()
        page.route("$testHost/**", routeViaClient(client))

        val dsl = BrowserDsl(testHost, client, page)
        try {
            dsl.logout()
            block(dsl)
        } catch (e: Exception) {
            dsl.screenshot("${name.methodName}-error")
            throw e
        }
        page.close()
    }

    private fun routeViaClient(client: HttpClient) = { it: Route ->
        val original = it.request().url()
        val newUrl = original.substringAfter(testHost)

        runBlocking {
            fun shared(builder: HttpRequestBuilder, it: Route) {
                it.request().headersArray().filter { !it.name.equals("Cookie", true) }.forEach {
                    builder.header(it.name, it.value)
                }
            }

            val response = when (it.request().method()) {
                "POST" -> client.post(newUrl) {
                    setBody(it.request().postDataBuffer())
                    shared(this, it)
                }
                "PUT" -> client.put(newUrl) {
                    setBody(it.request().postDataBuffer())
                    shared(this, it)
                }
                "DELETE" -> client.delete(newUrl) {
                    setBody(it.request().postDataBuffer())
                    shared(this, it)
                }
                else -> client.get(newUrl) {
                    shared(this, it)
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
                        val user = transaction { UserDao[id] }
                        call.sessions.set(Session(id, userEmail = user.email, userName = user.name, uniqueName = user.uniqueName, suspended = user.suspendedAt != null, admin = user.admin, curator = user.curator))
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
