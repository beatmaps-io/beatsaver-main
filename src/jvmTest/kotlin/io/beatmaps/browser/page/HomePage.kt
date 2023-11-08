package io.beatmaps.browser.page

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

class HomePage(page: Page) : PageBase(page) {
    private fun cards() = element(".beatmap").all()

    fun getMap(i: Int, block: MapCard.() -> Unit) {
        block(MapCard(cards()[i]))
    }

    class MapCard(element: Locator) : ElementBase(element) {
        val mapPageLink = element(".info > a")

        val bookmark = element(".additional > div > a:nth-child(1)")
        val addToPlaylist = element(".additional > div > a:nth-child(2)")
    }
}
