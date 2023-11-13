package io.beatmaps.browser.page

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

class MapPage(page: Page) : PageBase(page) {
    val tabs = Tabs(element(".nav-btn-group"))
    val scores = Scores(element(".scores"))
    val reviews = Reviews(element(".reviews"))

    class Tabs(element: Locator) : ElementBase(element) {
        val scoresaber = element("*[for='nav-ss']")
        val beatleader = element("*[for='nav-bl']")
        val reviews = element("*[for='nav-rv']")
    }

    class Scores(element: Locator) : ElementBase(element) {
        private fun rows() = element("tbody tr").all()
        fun count() = rows().size
        fun score(i: Int) = Score(rows()[i])

        val externalLink = element("th a")

        class Score(element: Locator) : ElementBase(element) {
            val name = element("td:nth-child(2)")
        }
    }

    class Reviews(element: Locator) : ElementBase(element) {
        val header = element(":scope > .card")
    }
}
