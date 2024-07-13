package io.beatmaps.browser.page

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import io.beatmaps.common.api.ReviewSentiment

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
        fun new(block: NewReview.() -> Unit) {
            block(NewReview(element(".card-body")))
        }

        private fun reviews() = element(":scope > .review-card").all()
        fun get(index: Int, block: Review.() -> Unit) {
            block(Review(reviews()[index]))
        }

        class NewReview(element: Locator) : ElementBase(element) {
            val text = element("textarea")
            val save = element("button[class~='float-end'], :scope > .d-grid button")

            fun sentiment(type: ReviewSentiment): Locator =
                element("button").all()[1 - type.dbValue]
        }

        class Review(element: Locator) : ElementBase(element) {
            val edit = element(".options i[class~='fa-pen']")
            val delete = element(".options i[class~='fa-trash']")

            val sentiment = element(".main > i")
            val text = element(".review-body > div:first-child")

            fun replies() = element(".replies .reply")

            fun edit(block: NewReview.() -> Unit) {
                block(NewReview(element(".review-body")))
            }

            fun reply(block: Reply.() -> Unit) {
                block(Reply(element(".reply-input")))
            }

            fun reply(index: Int, block: Reply.() -> Unit) {
                block(Reply(replies().all()[index]))
            }

            class Reply(element: Locator) : ElementBase(element) {
                val content = element(".content > *")
                val text = element("textarea")
                val save = element("button")

                val edit = element(".options i[class~='fa-pen']")
                val delete = element(".options i[class~='fa-trash']")
            }
        }
    }
}
