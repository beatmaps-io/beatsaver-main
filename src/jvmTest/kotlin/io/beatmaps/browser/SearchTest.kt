package io.beatmaps.browser

import io.beatmaps.common.api.AiDeclarationType
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import kotlin.test.assertEquals

class SearchTest : BrowserTestBase() {
    @Test
    fun `Can filter ai maps`() = bmTest {
        val username = transaction {
            val (userId, username) = createUser()
            createMap(userId, ai = AiDeclarationType.Uploader)

            username
        }

        homePage {
            navigate("/", ::waitForSearch)

            filters {
                aiMaps.click()
            }

            search("mapper:$username")

            assertEquals(1, cardCount())
        }
    }

    @Test
    fun `Can filter ranked maps`() = bmTest {
        val username = transaction {
            val (userId, username) = createUser()
            createMap(userId, ranked = true)
            createMap(userId, ranked = false)

            username
        }

        homePage {
            navigate("/", ::waitForSearch)

            filters {
                rankedMaps.click()
            }

            search("mapper:$username")

            assertEquals(1, cardCount())
        }
    }
}
