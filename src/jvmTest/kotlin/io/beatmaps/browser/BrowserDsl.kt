package io.beatmaps.browser

import com.amazonaws.services.s3.Headers
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.Request
import com.microsoft.playwright.Route
import com.microsoft.playwright.options.LoadState
import io.beatmaps.browser.page.HomePage
import io.beatmaps.browser.page.LoginPage
import io.beatmaps.browser.page.MapPage
import io.beatmaps.browser.page.Modals
import io.beatmaps.browser.page.PageHeader
import io.beatmaps.browser.page.RegisterPage
import io.beatmaps.browser.page.UploadPage
import io.beatmaps.browser.page.UserAccountPage
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
    fun navigate(url: String) {
        page.navigate("$testHost$url")
        waitForNetwork()
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

    fun url(): String = page.url()

    fun clipboard(): String? = page.evaluate("navigator.clipboard.readText()") as? String

    suspend fun login(id: Int? = null) {
        if (id == null) {
            client.get("/login-test")
        } else {
            client.get("/login-test/$id")
        }
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
    fun userAccountPage(block: UserAccountPage.() -> Unit) = block(UserAccountPage(page))
}
