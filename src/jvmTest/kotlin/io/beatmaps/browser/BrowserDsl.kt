package io.beatmaps.browser

import com.amazonaws.services.s3.Headers
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.Request
import com.microsoft.playwright.Route
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import com.microsoft.playwright.options.LoadState
import io.beatmaps.browser.page.HomePage
import io.beatmaps.browser.page.IssuePage
import io.beatmaps.browser.page.LoginPage
import io.beatmaps.browser.page.MapPage
import io.beatmaps.browser.page.Modals
import io.beatmaps.browser.page.PageHeader
import io.beatmaps.browser.page.PlaylistPage
import io.beatmaps.browser.page.RegisterPage
import io.beatmaps.browser.page.UploadPage
import io.beatmaps.browser.page.UserPage
import io.beatmaps.browser.util.FixtureHelpers
import io.beatmaps.common.json
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import kotlinx.serialization.SerializationStrategy
import org.apache.http.entity.ContentType
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Paths

class BrowserDsl(private val testHost: String, private val client: HttpClient, private val page: Page) : FixtureHelpers() {
    fun navigate(url: String, waitForLoad: () -> Unit = { waitForNetwork() }) {
        page.navigate("$testHost$url")
        waitForLoad()
    }

    fun reload(waitForLoad: () -> Unit = { waitForNetwork() }) {
        page.navigate(page.url())
        waitForLoad()
    }

    fun <T> mock(url: String, serializer: SerializationStrategy<T>, handler: (Request) -> T) {
        page.route("$testHost$url") {
            val response = handler(it.request())
            val json = json.encodeToString(serializer, response)

            it.fulfill(
                Route.FulfillOptions()
                    .setStatus(200)
                    .setBodyBytes(json.toByteArray())
                    .setHeaders(mapOf(Headers.CONTENT_TYPE to ContentType.APPLICATION_JSON.mimeType))
            )
        }
    }

    fun url(): String = page.url().removePrefix(testHost)

    fun clipboard(): String? = page.evaluate("navigator.clipboard.readText()") as? String

    suspend fun login(id: Int? = null) {
        if (id == null) {
            client.get("/login-test")
        } else {
            client.get("/login-test/$id")
        }
    }

    suspend fun logout() {
        client.get("/logout")
    }

    fun waitUntilGone(l: Locator) = waitUntilNOrLess(l, 0)

    fun waitUntilNOrLess(l: Locator, n: Int) {
        page.waitForCondition { l.count() <= n }
    }

    fun waitUntilNOrMore(l: Locator, n: Int) {
        page.waitForCondition { l.count() >= n }
    }

    private fun waitForNetwork() {
        page.waitForLoadState(LoadState.NETWORKIDLE)
    }

    fun assertVisible(locator: Locator, expected: Boolean) {
        with(assertThat(locator)) {
            if (expected) this else not()
        }.isVisible()
    }

    fun delay(t: Double) {
        page.waitForTimeout(t)
    }

    fun screenshot(name: String) {
        page.screenshot(
            Page.ScreenshotOptions()
                .setFullPage(true)
                .setPath(Paths.get("screenshots/$name.png"))
        )
    }

    private fun percy(name: String) {
        /*val css = client.get("/static/main.css").bodyAsText()
        private val percy = Percy(page)
        percy.snapshot(name, percyCSS = css)*/
    }

    fun <T> db(block: Transaction.() -> T) =
        transaction {
            maxAttempts = 1
            block()
        }

    val modals = Modals(page)

    fun pageHeader(block: PageHeader.() -> Unit) = block(PageHeader(page))
    fun homePage(block: HomePage.() -> Unit) = block(HomePage(page))
    fun uploadPage(block: UploadPage.() -> Unit) = block(UploadPage(page))
    fun mapPage(block: MapPage.() -> Unit) = block(MapPage(page))
    fun loginPage(block: LoginPage.() -> Unit) = block(LoginPage(page))
    fun registerPage(block: RegisterPage.() -> Unit) = block(RegisterPage(page))
    fun userPage(block: UserPage.() -> Unit) = block(UserPage(page))
    fun issuePage(block: IssuePage.() -> Unit) = block(IssuePage(page))
    fun playlistPage(block: PlaylistPage.() -> Unit) = block(PlaylistPage(page))
}
