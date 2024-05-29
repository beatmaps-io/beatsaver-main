package io.beatmaps.browser.page

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

class MapPage(page: Page) : PageBase(page) {
    val tabs = Tabs(element(".nav-minimal"))
    val scores = Scores(element(".scores"))
    val reviews = Reviews(element(".reviews"))

    class Tabs(element: Locator) : ElementBase(element) {
        val scoresaber = element("#nav-ss")
        val beatleader = element("#nav-bl")
        val reviews = element("#nav-rv")
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
