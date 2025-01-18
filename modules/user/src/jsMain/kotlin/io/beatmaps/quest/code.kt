package io.beatmaps.quest

import external.Axios
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.QuestCode
import io.beatmaps.api.QuestCodeResponse
import io.beatmaps.shared.form.errors
import io.beatmaps.util.fcmemo
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.form
import react.dom.html.ReactHTML.input
import react.router.useLocation
import react.useEffect
import react.useRef
import web.cssom.ClassName
import web.html.ButtonType
import web.html.HTMLInputElement
import web.html.InputType
import web.url.URLSearchParams

external interface QuestCodeProps : Props {
    var deviceCodeCallback: (String, QuestCodeResponse) -> Unit
    var setError: (Boolean) -> Unit
    var error: Boolean
}

val questCode = fcmemo<QuestCodeProps>("questCode") { props ->
    val location = useLocation()
    val inputRef = useRef<HTMLInputElement>()

    fun onSubmit() {
        val code = inputRef.current?.value ?: ""

        if (code.isNotEmpty()) {
            Axios.post<QuestCodeResponse>(
                "${Config.apibase}/quest/code",
                QuestCode(code),
                generateConfig<QuestCode, QuestCodeResponse>()
            ).then { res ->
                props.deviceCodeCallback(code, res.data)
            }.catch {
                props.setError(true)
            }
        }
    }

    useEffect(location) {
        val params = URLSearchParams(location.search)
        inputRef.current?.value = params["code"] ?: ""
        onSubmit()
    }

    div {
        className = ClassName("login-form card border-dark")
        div {
            className = ClassName("card-header")
            +"Enter code"
        }
        form {
            className = ClassName("card-body")
            onSubmit = { ev ->
                ev.preventDefault()
                onSubmit()
            }

            div {
                className = ClassName("quest-errors")
                if (props.error) {
                    errors {
                        errors = listOf("Code not recognised. Try again.")
                    }
                }
            }

            input {
                key = "code"
                type = InputType.text
                className = ClassName("form-control quest-code")
                maxLength = 8
                placeholder = "Enter code"
                onChange = {
                    props.setError(false)
                }
                ref = inputRef
            }

            div {
                className = ClassName("d-grid")
                button {
                    className = ClassName("btn btn-success mb-2 mt-4")
                    type = ButtonType.submit
                    +"Continue"
                }
            }
        }
    }
}
