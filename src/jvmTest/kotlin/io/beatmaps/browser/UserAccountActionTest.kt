package io.beatmaps.browser

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import kotlin.time.Duration.Companion.days

class UserAccountActionTest : BrowserTestBase() {
    @Test
    fun `Can change username`() = bmTest {
        val (uid, name) = transaction {
            createUser(2.days)
        }

        login(uid)
        navigate("/profile#account")

        userPage {
            accountTab {
                username.change.click()
                assertThat(username.errors()).hasText("That's already your username!")

                // We could create a second account and use that for this test but "test" should always exist
                username.field.fill("test")
                username.change.click()
                assertThat(username.errors()).hasText("Username already taken")

                username.field.fill("username with spaces")
                username.change.click()
                assertThat(username.errors()).hasText("Username not valid")

                username.field.fill("${name}2")
                username.change.click()
                assertThat(userInfo.username()).hasText("${name}2")

                username.field.fill(name)
                username.change.click()
                assertThat(username.errors()).hasText("You can only set a new username once per day")
            }
        }
    }
}
