package io.beatmaps.browser.page

import com.microsoft.playwright.Page

class RegisterPage(page: Page) : PageBase(page) {
    val heading = element(".card-header")
    val body = element(".card-body")

    val usernameField = id("username")
    val emailField = id("email")
    val passwordField = id("password")
    val repeatPasswordField = id("password2")

    val register = element(".card-body button")
}
