package io.beatmaps.user

import external.routeLink
import io.beatmaps.setPageTitle
import kotlinx.browser.window
import kotlinx.html.ButtonType
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import org.w3c.dom.url.URLSearchParams
import react.Props
import react.PropsWithChildren
import react.RBuilder
import react.RComponent
import react.State
import react.dom.a
import react.dom.button
import react.dom.div
import react.dom.form
import react.dom.hr
import react.dom.i
import react.dom.input
import react.dom.jsStyle
import react.dom.p
import react.dom.span
import react.fc

external interface LoginFormProps : PropsWithChildren {
    var buttonText: String
    var discordLink: String?
}

val loginForm = fc<LoginFormProps> { props ->
    a(href = props.discordLink ?: "/discord", classes = "btn discord-btn") {
        span {
            i("fab fa-discord") {}
            +" Sign in with discord"
        }
    }
    p {
        +"OR"
    }
    props.children()
    input(type = InputType.text, classes = "form-control") {
        key = "username"
        attrs.name = "username"
        attrs.placeholder = "Username"
        attrs.required = true
        attrs.autoFocus = true
        attrs.attributes["autocomplete"] = "username"
    }
    input(type = InputType.password, classes = "form-control") {
        key = "password"
        attrs.name = "password"
        attrs.placeholder = "Password"
        attrs.required = true
        attrs.attributes["autocomplete"] = "current-password"
    }
    div("d-grid") {
        button(classes = "btn btn-success", type = ButtonType.submit) {
            i("fas fa-sign-in-alt") {}
            +" ${props.buttonText}"
        }
    }
}

class LoginPage : RComponent<Props, State>() {
    override fun componentDidMount() {
        setPageTitle("Login")
    }

    override fun RBuilder.render() {
        div("login-form card border-dark") {
            div("card-header") {
                +"Sign in"
            }
            form(classes = "card-body", method = FormMethod.post, action = "/login") {
                loginForm {
                    attrs.buttonText = "Sign in"

                    val params = URLSearchParams(window.location.search)
                    if (params.has("failed")) {
                        div("invalid-feedback") {
                            attrs.jsStyle {
                                display = "block"
                            }
                            +"Username or password not valid"
                        }
                    } else if (params.has("valid")) {
                        div("valid-feedback") {
                            attrs.jsStyle {
                                display = "block"
                            }
                            +"Account activated, you can now login"
                        }
                    } else if (params.has("reset")) {
                        div("valid-feedback") {
                            attrs.jsStyle {
                                display = "block"
                            }
                            +"Password reset, you can now login"
                        }
                    }
                }
                routeLink("/forgot", className = "forgot_pwd") {
                    +"Forgot password?" // Send the user a JWT that will allow them to reset the password until it expires in ~20 mins
                }
                hr {}
                div("d-grid") {
                    routeLink("/register", className = "btn btn-primary") {
                        i("fas fa-user-plus") {}
                        +" Sign up new account"
                    }
                }
            }
        }
    }
}

fun RBuilder.loginPage(handler: Props.() -> Unit) =
    child(LoginPage::class) {
        this.attrs(handler)
    }
