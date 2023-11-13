package io.beatmaps.browser.page

import com.microsoft.playwright.Page

class LoginPage(page: Page) : PageBase(page) {
    val heading = element(".card-header")
    val register = id("register")
}
