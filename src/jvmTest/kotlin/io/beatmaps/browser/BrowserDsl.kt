package io.beatmaps.browser

import com.appmattus.kotlinfixture.kotlinFixture
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.LoadState
import io.beatmaps.browser.page.HomePage
import io.beatmaps.browser.page.LoginPage
import io.beatmaps.browser.page.Modals
import io.beatmaps.browser.page.PageHeader
import io.beatmaps.browser.page.RegisterPage
import io.beatmaps.common.dbo.User
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Paths

class BrowserDsl(private val testHost: String, private val client: HttpClient, private val page: Page) {
    val fixture = kotlinFixture()

    fun navigate(url: String) {
        page.navigate("$testHost$url")
        page.waitForLoadState(LoadState.NETWORKIDLE)
    }

    fun createUser(): Pair<Int, String> {
        val now = Clock.System.now().epochSeconds
        val fuzz = fixture(1..100000)

        val username = "test-$now-$fuzz"

        return transaction {
            User.insertAndGetId {
                it[name] = username
                it[email] = "$username@beatsaver.com"
                it[password] = null
                it[verifyToken] = null
                it[uniqueName] = username
                it[active] = true
            }.value to username
        }
    }

    suspend fun login(id: Int? = null) {
        if (id == null) {
            client.get("/login-test")
        } else {
            client.get("/login-test/$id")
        }
    }

    fun waitUntilGone(l: Locator) {
        page.waitForCondition { l.count() == 0 }
    }

    fun delay(t: Double) {
        page.waitForTimeout(t)
    }

    fun screenshot(prefix: String) {
        val time = Clock.System.now().toEpochMilliseconds()
        page.screenshot(Page.ScreenshotOptions().setPath(Paths.get("screenshots/$prefix-$time.png")))
    }

    val modals = Modals(page)

    fun pageHeader(block: PageHeader.() -> Unit) = block(PageHeader(page))
    fun homePage(block: HomePage.() -> Unit) = block(HomePage(page))
    fun loginPage(block: LoginPage.() -> Unit) = block(LoginPage(page))
    fun registerPage(block: RegisterPage.() -> Unit) = block(RegisterPage(page))
}
