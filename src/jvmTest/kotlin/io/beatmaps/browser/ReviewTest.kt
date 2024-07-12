package io.beatmaps.browser

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.beatmaps.common.api.ReviewSentiment
import io.beatmaps.common.dbo.Review
import io.beatmaps.common.dbo.ReviewDao
import io.beatmaps.common.dbo.ReviewReply
import io.beatmaps.common.dbo.ReviewReplyDao
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.junit.Test
import java.lang.Integer.toHexString
import java.util.regex.Pattern
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ReviewTest : BrowserTestBase() {
    @Test
    fun `Can post, edit and delete reviews and replies`() = bmTest {
        val mapId = newSuspendedTransaction {
            val (uid, _) = createUser()
            val (mid, _) = createMap(uid, true)

            mid
        }

        // Login as another user as you can't review your own map
        login(1) // Test user
        navigate("/maps/${toHexString(mapId)}")

        mapPage {
            tabs.reviews.click()

            val demoText = fixture<String>()
            reviews.new {
                sentiment(ReviewSentiment.POSITIVE).click()
                text.fill(demoText)
                save.click()

                waitUntilGone(elem)
            }

            val reviewId = db {
                ReviewDao.wrapRow(Review.selectAll().where { Review.mapId eq mapId }.single()).let { review ->
                    assertEquals(demoText, review.text)
                    assertEquals(ReviewSentiment.POSITIVE.dbValue, review.sentiment)

                    review.id.value
                }
            }

            val editText = fixture<String>()
            reviews.get(0) {
                assertThat(sentiment).hasClass(Pattern.compile("text-success"))
                assertEquals(demoText, text.innerText())
                assert(edit.isVisible) { "Edit button missing" }

                edit.click()
                edit {
                    sentiment(ReviewSentiment.NEGATIVE).click()
                    text.fill(editText)
                    save.click()
                }

                assertThat(sentiment).hasClass(Pattern.compile("text-danger"))
                assertEquals(editText, text.innerText())

                db {
                    ReviewDao.wrapRow(Review.selectAll().where { Review.mapId eq mapId }.single()).let { review ->
                        assertEquals(editText, review.text)
                        assertEquals(ReviewSentiment.NEGATIVE.dbValue, review.sentiment)
                    }
                }

                val replyText = fixture<String>()
                val replyEditText = fixture<String>()
                reply {
                    text.fill(replyText)
                    save.click()
                }

                waitUntilNOrMore(replies(), 1)
                reply(0) {
                    content.hover()

                    assertEquals(replyText, content.innerText())
                    db {
                        assertEquals(
                            replyText,
                            ReviewReplyDao.wrapRow(ReviewReply.selectAll().where { ReviewReply.reviewId eq reviewId }.single()).text
                        )
                    }

                    assert(edit.isVisible) { "Edit button missing" }
                    edit.click()
                    text.fill(replyEditText)
                    save.click()

                    waitUntilGone(save)
                    assertEquals(replyEditText, content.innerText())
                    db {
                        assertEquals(
                            replyEditText,
                            ReviewReplyDao.wrapRow(ReviewReply.selectAll().where { ReviewReply.reviewId eq reviewId }.single()).text
                        )
                    }

                    screenshot("review")

                    delete.click()
                    modals.confirmModal {
                        confirm.click()
                    }
                    assertThat(content).hasText("This reply has been deleted.")

                    db {
                        assertNotNull(
                            ReviewReplyDao.wrapRow(ReviewReply.selectAll().where { ReviewReply.reviewId eq reviewId }.single()).deletedAt
                        ) { "Should be deleted" }
                    }
                }

                delete.click()
                modals.confirmModal {
                    confirm.click()
                }
                assertThat(elem).not().isVisible()
            }

            db {
                val replyCount = ReviewReply.selectAll().where { ReviewReply.reviewId eq reviewId }.count()
                assertEquals(0, replyCount)

                assertNotNull(
                    ReviewDao.wrapRow(Review.selectAll().where { Review.mapId eq mapId }.single()).deletedAt
                ) { "Should be deleted" }
            }
        }
    }
}
