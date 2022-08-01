package io.beatmaps.user

import external.axiosGet
import io.beatmaps.api.UserDetail
import io.beatmaps.common.Config
import io.beatmaps.common.json
import io.beatmaps.setPageTitle
import kotlinx.browser.window
import kotlinx.html.ButtonType
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.serialization.decodeFromString
import org.w3c.dom.url.URLSearchParams
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
import react.dom.a
import react.dom.b
import react.dom.br
import react.dom.button
import react.dom.div
import react.dom.form
import react.dom.i
import react.dom.img
import react.dom.input
import react.dom.jsStyle
import react.dom.p
import react.dom.span
import react.setState

external interface AuthorizePageState : RState {
    var loading: Boolean
    var username: String?
    var avatar: String?
}

class AuthorizePage : RComponent<RProps, AuthorizePageState>() {
    override fun componentWillMount() {
        setState {
            loading = false
            username = null
        }
    }
    override fun componentDidMount() {
        setPageTitle("Login with BeatSaver")

        loadState()
    }

    private fun loadState() {
        setState {
            loading = true
        }

        axiosGet<String>(
            "${Config.apibase}/users/me"
        ).then {
            // Decode is here so that 401 actually passes to error handler
            val data = json.decodeFromString<UserDetail>(it.data)

            setState {
                loading = false
                username = data.name
                avatar = data.avatar
            }
        }.catch {
            setState {
                loading = false
                username = null
                avatar = null
            }
        }
    }

    override fun RBuilder.render() {
        val params = URLSearchParams(window.location.search)
        val clientId = params.get("client_id") ?: ""
        val scopes = (params.get("scope") ?: "").split(",")

        div("login-form card border-dark") {
            div("card-header") {
                b {
                    +clientId
                }
                br {}
                +" wants to access your BeatSaver account"
                br {}
                state.username?.let {
                    +"Hi, "
                    b {
                        +it
                    }
                    +" "
                    img(src = state.avatar, classes = "authorize-avatar") {}
                    br {}

                    a(href = "authorize/not-me" + window.location.search) {
                        +"Not you?"
                    }
                }
            }
            div("scopes") {
                span("scopes-description") {
                    +"This will allow "
                    +clientId
                    +" to:"
                }

                for (scope in scopes) {
                    div("scope") {
                        when (scope) {
                            "identity" -> {
                                i("fas fa-user-check") {}
                                span { b { +"  Access your id, username and avatar" } }
                            }
                            else -> span { b { +"  Replace all your maps with RickRoll" } }
                        }
                    }
                }
            }
            if (state.loading) {
                span { +"Loading..." }
            } else if (state.username != null) {
                a(href = "/oauth2/authorize/success" + window.location.search) {
                    div("d-grid") {
                        button(classes = "btn btn-success", type = ButtonType.submit) {
                            i("fas fa-sign-in-alt") {}
                            +" Authorize"
                        }
                    }
                }
            } else {
                form(
                    classes = "card-body",
                    method = FormMethod.post,
                    action = "/oauth2/authorize" + window.location.search
                ) {
                    val serialized = window.location.search.encodeToByteArray().joinToString("") { (0xFF and it.toInt()).toString(16).padStart(2, '0') }

                    a(href = "/discord?state=$serialized", classes = "btn discord-btn") {
                        span {
                            i("fab fa-discord") {}
                            +" Sign in with discord"
                        }
                    }
                    p {
                        +"OR"
                    }
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
                            +" Authorize"
                        }
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
