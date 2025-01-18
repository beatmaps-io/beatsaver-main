package io.beatmaps.user

import external.routeLink
import io.beatmaps.setPageTitle
import io.beatmaps.shared.form.errors
import io.beatmaps.util.fcmemo
import io.beatmaps.util.form
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.hr
import react.dom.html.ReactHTML.i
import react.useEffectOnce
import web.cssom.ClassName
import web.form.FormMethod
import web.url.URLSearchParams
import web.window.window

val loginPage = fcmemo<Props>("loginPage") {
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

    div {
        className = ClassName("login-form card border-dark")
        div {
            className = ClassName("card-header")
            +"Sign in"
        }
        form("card-body", FormMethod.post, "/login") {
            loginForm {
                buttonText = "Sign in"

                getToast()?.let {
                    div {
                        className = ClassName("mb-2")
                        errors {
                            valid = it.second
                            errors = listOf(it.first)
                        }
                    }
                }
            }
            routeLink("/forgot", className = "forgot_pwd") {
                +"Forgot password?" // Send the user a JWT that will allow them to reset the password until it expires in ~20 mins
            }
            hr {}
            div {
                className = ClassName("d-grid")
                routeLink("/register", className = "btn btn-primary", id = "register") {
                    i {
                        className = ClassName("fas fa-user-plus")
                    }
                    +" Sign up new account"
                }
            }
        }
    }
}
