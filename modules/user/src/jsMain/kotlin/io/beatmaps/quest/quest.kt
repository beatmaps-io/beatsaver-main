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
import web.url.URLSearchParams

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
            className = ClassName("login-form card border-dark")
            div {
                className = ClassName("card-header")
                +"Authorization complete"
            }
            div {
                className = ClassName("card-body")
                i {
                    className = ClassName("fas fa-check-circle code-complete")
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
            className = ClassName("login-form card border-dark")
            oauthHeader {
                this.clientName = clientName
                clientIcon = codeResponse.clientIcon
                callback = {
                    setLoggedIn(it)
                }
                logoutLink = "/quest/not-me?code=$code"
            }
            oauthScopes {
                this.clientName = clientName
                scopes = codeResponse.scopes
            }

            if (loggedIn == null) {
                div {
                    className = ClassName("card-body")
                    span { +"Loading..." }
                }
            } else if (loggedIn) {
                div {
                    className = ClassName("card-body d-grid")
                    button {
                        className = ClassName("btn btn-success")
                        onClick = {
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
                            className = ClassName("fas fa-sign-in-alt")
                        }
                        +" Authorize"
                    }
                }
            } else {
                form("card-body", FormMethod.post, "/quest?code=$code") {
                    loginForm {
                        if (params.has("failed")) {
                            errors {
                                errors = listOf("Username or password not valid")
                            }
                        }

                        val serialized = "?code=$code".encodeToByteArray().joinToString("") {
                            (0xFF and it.toInt()).toString(16).padStart(2, '0')
                        }

                        discordLink = "/discord?state=$serialized"
                        buttonText = "Login"
                    }
                }
            }
        }
    } else {
        questCode {
            this.error = error
            this.setError = {
                setError(it)
            }
            deviceCodeCallback = { code, response ->
                setCode(code)
                setCodeResponse(response)
            }
        }
    }
}
