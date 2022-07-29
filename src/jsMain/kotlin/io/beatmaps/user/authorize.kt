package io.beatmaps.user

import io.beatmaps.setPageTitle
import kotlinx.browser.window
import kotlinx.html.ButtonType
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import org.w3c.dom.url.URLSearchParams
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
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
import react.router.dom.routeLink

class AuthorizePage : RComponent<RProps, RState>() {
    override fun componentDidMount() {
        setPageTitle("Login with BeatSaver")
    }

    override fun RBuilder.render() {
        div("login-form card border-dark") {
            val params = URLSearchParams(window.location.search)
            div("card-header") {
                +"Log in to "
                +(params.get("client_id") ?: "")
                +" with BeatSaver"
            }
            form(classes = "card-body", method = FormMethod.post, action = "/oauth2/authorize" + window.location.search) {
                a(href = "/discord", classes = "btn discord-btn") {
                    span {
                        i("fab fa-discord") {}
                        +" Sign in with discord"
                    }
                }
                p {
                    +"OR"
                }
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
                        +" Sign in"
                    }
                }
            }
        }
    }
}

fun RBuilder.authorizePage(handler: RProps.() -> Unit): ReactElement {
    return child(AuthorizePage::class) {
        this.attrs(handler)
    }
}
