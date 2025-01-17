package io.beatmaps.quest

import external.Axios
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.QuestCodeResponse
import io.beatmaps.api.QuestComplete
import io.beatmaps.setPageTitle
import io.beatmaps.shared.form.errors
import io.beatmaps.user.loginForm
import io.beatmaps.user.oauth.oauthHeader
import io.beatmaps.user.oauth.oauthScopes
import io.beatmaps.util.fcmemo
import io.beatmaps.util.form
import org.w3c.dom.url.URLSearchParams
import react.Props
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.span
import react.router.useLocation
import react.useEffectOnce
import react.useState
import web.cssom.ClassName
import web.form.FormMethod

val quest = fcmemo<Props>("quest") {
    useEffectOnce {
        setPageTitle("Link device")
    }

    val location = useLocation()
    val params = URLSearchParams(location.search)

    val (error, setError) = useState(false)
    val (codeResponse, setCodeResponse) = useState<QuestCodeResponse>()
    val (code, setCode) = useState<String>()
    val (complete, setComplete) = useState(false)
    val (loggedIn, setLoggedIn) = useState<Boolean>()

    if (complete) {
        div {
            attrs.className = ClassName("login-form card border-dark")
            div {
                attrs.className = ClassName("card-header")
                +"Authorization complete"
            }
            div {
                attrs.className = ClassName("card-body")
                i {
                    attrs.className = ClassName("fas fa-check-circle code-complete")
                }
                span {
                    +"You're good to go!"
                    br {}
                    +"Return to your device now."
                }
            }
        }
    } else if (codeResponse != null) {
        val clientName = codeResponse.clientName ?: "An unknown application"
        div {
            attrs.className = ClassName("login-form card border-dark")
            oauthHeader {
                attrs.clientName = clientName
                attrs.clientIcon = codeResponse.clientIcon
                attrs.callback = {
                    setLoggedIn(it)
                }
                attrs.logoutLink = "/quest/not-me?code=$code"
            }
            oauthScopes {
                attrs.clientName = clientName
                attrs.scopes = codeResponse.scopes
            }

            if (loggedIn == null) {
                div {
                    attrs.className = ClassName("card-body")
                    span { +"Loading..." }
                }
            } else if (loggedIn) {
                div {
                    attrs.className = ClassName("card-body d-grid")
                    button {
                        attrs.className = ClassName("btn btn-success")
                        attrs.onClick = {
                            Axios.post<String>(
                                "${Config.apibase}/quest/complete",
                                QuestComplete(codeResponse.deviceCode),
                                generateConfig<QuestComplete, String>()
                            ).then { _ ->
                                setComplete(true)
                            }.catch {
                                setCodeResponse(null)
                                setError(true)
                            }
                        }

                        i {
                            attrs.className = ClassName("fas fa-sign-in-alt")
                        }
                        +" Authorize"
                    }
                }
            } else {
                form("card-body", FormMethod.post, "/quest?code=$code") {
                    loginForm {
                        if (params.has("failed")) {
                            errors {
                                attrs.errors = listOf("Username or password not valid")
                            }
                        }

                        val serialized = "?code=$code".encodeToByteArray().joinToString("") {
                            (0xFF and it.toInt()).toString(16).padStart(2, '0')
                        }

                        attrs.discordLink = "/discord?state=$serialized"
                        attrs.buttonText = "Login"
                    }
                }
            }
        }
    } else {
        questCode {
            attrs.error = error
            attrs.setError = {
                setError(it)
            }
            attrs.deviceCodeCallback = { code, response ->
                setCode(code)
                setCodeResponse(response)
            }
        }
    }
}
