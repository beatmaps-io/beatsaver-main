package io.beatmaps.browser.page

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

class PageHeader(page: Page) : PageBase(page) {
    val login = element("#login")
    val new = NewDropdown(element("#dropdown-new"))

    class NewDropdown(elem: Locator) : ElementBase(elem) {
        val btn = element(":scope > a")
        val map = element("div a:nth-child(1)")
        val playlist = element("div a:nth-child(2)")
    }
}
