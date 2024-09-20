package io.beatmaps.browser

import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import kotlin.test.assertEquals
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
            assertEquals("That's already your username!", usernameErrors().innerText())

            // We could create a second account and use that for this test but "test" should always exist
            usernameField.fill("test")
            usernameChange.click()
            assertEquals("Username already taken", usernameErrors().innerText())

            usernameField.fill("username with spaces")
            usernameChange.click()
            assertEquals("Username not valid", usernameErrors().innerText())

            usernameField.fill("${username}2")
            usernameChange.click()
            usernameField.waitFor() // Wait for page to load again
            assertEquals("${username}2", username().innerText())

            usernameField.fill(username)
            usernameChange.click()
            assertEquals("You can only set a new username once per day", usernameErrors().innerText())
        }
    }
}
