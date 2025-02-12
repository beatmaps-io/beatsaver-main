package io.beatmaps.browser.page

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

class HomePage(page: Page) : PageBase(page) {
    private fun cardSelector() = element(".beatmap")
    fun cardCount() = cardSelector().count()
    private fun cards() = cardSelector().all()

    val searchBox = element("form input[type='search']")
    val searchButton = element("form button[type='submit']")
    val filterDropdown = element(".filter-dropdown")
    private val filters = SearchFilters(element(".filter-container .dropdown-menu"))

    fun search(text: String) {
        searchBox.fill(text)
        searchButton.click()
    }

    fun getMap(i: Int, block: MapCard.() -> Unit) {
        block(MapCard(cards()[i]))
    }

    fun filters(block: SearchFilters.() -> Unit) {
        if (!filters.visible()) filterDropdown.click()
        block(filters)
        filterDropdown.click()
    }

    class SearchFilters(element: Locator): ElementBase(element) {
        fun visible() = elem.isVisible
        val aiMaps = element("[for=bot-ai]")
        val allMaps = element("[for=bot-all]")
        val humanMaps = element("[for=bot-human]")
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
