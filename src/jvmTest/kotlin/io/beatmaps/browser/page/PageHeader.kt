package io.beatmaps.browser.page

import com.microsoft.playwright.Page

class PageHeader(page: Page) : PageBase(page) {
    val login = element("#login")
}
