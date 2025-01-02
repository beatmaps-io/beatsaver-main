package io.beatmaps.browser.page

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

class UserPage(page: Page) : PageBase(page) {
    val userInfo = UserInfo(element(".user-info"))

    private val accountTab = AccountTab(element(".container"))
    fun accountTab(block: AccountTab.() -> Unit) = block(accountTab)

    class AccountTab(element: Locator) : ElementBase(element) {
        val username = UserName(element("#change-username"))

        class UserName(element: Locator) : ElementBase(element) {
            val change = element("button")
            val field = element("input")
            fun errors() = element(".invalid-feedback")
        }
    }

    class UserInfo(element: Locator) : ElementBase(element) {
        fun username() = element("h4")

        val playlistDownload = element("#dl-playlist")
        val playlistOneClick = element("#oc-playlist")

        val follow = element("#follow")
        val followDropdown = element("#follow-dd")
        val followUploads = element("#follow-uploads")
        val followCurations = element("#follow-curations")
        val followCollabs = element("#follow-collabs")

        val report = element("#report")
    }
}
