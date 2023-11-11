package io.beatmaps.browser.page

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

class HomePage(page: Page) : PageBase(page) {
    private fun cards() = element(".beatmap").all()
    val searchBox = element("form input[type='search']")
    val searchButton = element("form button[type='submit']")

    fun search(text: String) {
        searchBox.fill(text)
        searchButton.click()
    }

    fun getMap(i: Int, block: MapCard.() -> Unit) {
        block(MapCard(cards()[i]))
    }

    class MapCard(element: Locator) : ElementBase(element) {
        val mapPageLink = element(".info > a")

        val bsr = element(".links > a:nth-child(1)")
        val preview = element(".links > a:nth-child(2)")
        val oneClick = element(".links > a:nth-child(3)")
        val download = element(".links > a:nth-child(4)")

        val bookmark = element(".additional > div > a:nth-child(1)")
        val addToPlaylist = element(".additional > div > a:nth-child(2)")
    }
}
