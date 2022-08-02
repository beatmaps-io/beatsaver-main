package io.beatmaps.user

import external.axiosGet
import io.beatmaps.api.UserDetail
import io.beatmaps.common.Config
import io.beatmaps.common.json
import io.beatmaps.setPageTitle
import kotlinx.browser.window
import kotlinx.html.ButtonType
import kotlinx.html.FormMethod
import kotlinx.serialization.decodeFromString
import org.w3c.dom.get
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
        val oauth = window["oauth"]
        val clientName = oauth?.name?.toString() ?: "An unknown application"
        val scopes = (params.get("scope") ?: "").split(",")

        div("login-form card border-dark") {
            div("card-header") {
                b {
                    +clientName
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
                    +clientName
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
                val search = window.location.search
                form(classes = "card-body", method = FormMethod.post, action = "/oauth2/authorize$search") {
                    val serialized = search.encodeToByteArray().joinToString("") {
                        (0xFF and it.toInt()).toString(16).padStart(2, '0')
                    }

                    loginForm {
                        attrs.discordLink = "/discord?state=$serialized"
                        attrs.buttonText = "Authorize"
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
