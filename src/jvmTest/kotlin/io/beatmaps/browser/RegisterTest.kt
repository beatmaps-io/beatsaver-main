package io.beatmaps.browser

import com.toxicbakery.bcrypt.Bcrypt
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.UserDao
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RegisterTest : BrowserTestBase() {
    @Test
    fun `Can register a new user`() = bmTest {
        navigate("/")
        pageHeader {
            login.click()
        }
        loginPage {
            assertEquals("Sign in", heading.innerText())
            register.click()
        }
        registerPage {
            assertEquals("Register", heading.innerText())

            val now = Clock.System.now().epochSeconds
            val fuzz = fixture(1..100000)
            val username = "test-$now-$fuzz"

            usernameField.fill(username)
            emailField.fill("$username@beatsaver.com")
            passwordField.fill("hunter22")
            repeatPasswordField.fill("hunter22")

            register.click()

            try {
                waitUntilGone(usernameField)
            } finally {
                screenshot("captcha")
            }
            assertContains(body.innerText(), "success")

            val dao = transaction {
                UserDao.wrapRow(User.selectAll().where { User.uniqueName eq "test-$now-$fuzz" }.single())
            }

            assertEquals(dao.email, "test-$now-$fuzz@beatsaver.com")
            assertTrue(Bcrypt.verify("hunter22", dao.password!!.toByteArray()), "Password invalid")
        }
    }

    @Test
    fun takeScreenshots() = bmTest {
        navigate("/")
        screenshot("home")
        homePage {
            getMap(0) {
                mapPageLink.click()
            }
        }
        screenshot("map")
        pageHeader {
            login.click()
        }
        screenshot("login")
    }
}
