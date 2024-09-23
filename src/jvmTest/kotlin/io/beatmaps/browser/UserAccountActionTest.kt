package io.beatmaps.browser

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import kotlin.time.Duration.Companion.days

class UserAccountActionTest : BrowserTestBase() {
    @Test
    fun `Can change username`() = bmTest {
        val (uid, username) = transaction {
            createUser(2.days)
        }

        login(uid)
        navigate("/profile#account")

        userAccountPage {
            usernameChange.click()
            assertThat(usernameErrors()).hasText("That's already your username!")

            // We could create a second account and use that for this test but "test" should always exist
            usernameField.fill("test")
            usernameChange.click()
            assertThat(usernameErrors()).hasText("Username already taken")

            usernameField.fill("username with spaces")
            usernameChange.click()
            assertThat(usernameErrors()).hasText("Username not valid")

            usernameField.fill("${username}2")
            usernameChange.click()
            assertThat(username()).hasText("${username}2")

            usernameField.fill(username)
            usernameChange.click()
            assertThat(usernameErrors()).hasText("You can only set a new username once per day")
        }
    }
}
