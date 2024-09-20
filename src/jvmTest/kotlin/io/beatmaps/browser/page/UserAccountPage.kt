package io.beatmaps.browser.page

import com.microsoft.playwright.Page

class UserAccountPage(page: Page) : PageBase(page) {
    private val userInfoContainer = element(".user-info")
    private val usernameContainer = element("#change-username")

    fun username() = userInfoContainer.locator("h4")

    val usernameChange = usernameContainer.locator("button")
    val usernameField = usernameContainer.locator("input")
    fun usernameErrors() = usernameContainer.locator(".invalid-feedback")
}
