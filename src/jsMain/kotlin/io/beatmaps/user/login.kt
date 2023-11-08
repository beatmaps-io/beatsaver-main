package io.beatmaps.user

import external.routeLink
import io.beatmaps.setPageTitle
import io.beatmaps.shared.form.errors
import kotlinx.browser.window
import kotlinx.html.FormMethod
import org.w3c.dom.url.URLSearchParams
import react.Props
import react.dom.div
import react.dom.form
import react.dom.hr
import react.dom.i
import react.fc
import react.useEffectOnce

val loginPage = fc<Props> {
    useEffectOnce {
        setPageTitle("Login")
    }

    fun getToast() =
        URLSearchParams(window.location.search).let { params ->
            if (params.has("failed")) {
                "Username or password not valid" to false
            } else if (params.has("valid")) {
                "Account activated, you can now login" to true
            } else if (params.has("reset")) {
                "Password reset, you can now login" to true
            } else if (params.has("email")) {
                "Email changed, you must log in again" to true
            } else {
                null
            }
        }

    div("login-form card border-dark") {
        div("card-header") {
            +"Sign in"
        }
        form(classes = "card-body", method = FormMethod.post, action = "/login") {
            loginForm {
                attrs.buttonText = "Sign in"

                getToast()?.let {
                    div("mb-2") {
                        errors {
                            attrs.valid = it.second
                            attrs.errors = listOf(it.first)
                        }
                    }
                }
            }
            routeLink("/forgot", className = "forgot_pwd") {
                +"Forgot password?" // Send the user a JWT that will allow them to reset the password until it expires in ~20 mins
            }
            hr {}
            div("d-grid") {
                routeLink("/register", className = "btn btn-primary", id = "register") {
                    i("fas fa-user-plus") {}
                    +" Sign up new account"
                }
            }
        }
    }
}
