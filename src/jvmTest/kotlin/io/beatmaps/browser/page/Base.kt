package io.beatmaps.browser.page

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

abstract class PlaywrightBase {
    protected abstract fun element(selector: String): Locator
    protected fun id(id: String) = element("#$id")
}

abstract class PageBase(private val page: Page) : PlaywrightBase() {
    override fun element(selector: String): Locator = page.locator(selector)
}

abstract class ElementBase(val elem: Locator) : PlaywrightBase() {
    override fun element(selector: String): Locator = elem.locator(selector)
}
