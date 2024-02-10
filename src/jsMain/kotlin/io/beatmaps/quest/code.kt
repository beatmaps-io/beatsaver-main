package io.beatmaps.quest

import external.Axios
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.QuestCode
import io.beatmaps.api.QuestCodeResponse
import io.beatmaps.shared.form.errors
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onSubmitFunction
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.url.URLSearchParams
import react.Props
import react.dom.button
import react.dom.div
import react.dom.form
import react.dom.input
import react.fc
import react.router.useLocation
import react.useEffect
import react.useRef

external interface QuestCodeProps : Props {
    var deviceCodeCallback: (String, QuestCodeResponse) -> Unit
    var setError: (Boolean) -> Unit
    var error: Boolean
}

val questCode = fc<QuestCodeProps> { props ->
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
        inputRef.current?.value = params.get("code") ?: ""
        onSubmit()
    }

    div("login-form card border-dark") {
        div("card-header") {
            +"Enter code"
        }
        form(classes = "card-body") {
            attrs.onSubmitFunction = { ev ->
                ev.preventDefault()
                onSubmit()
            }

            div("quest-errors") {
                if (props.error) {
                    errors {
                        attrs.errors = listOf("Code not recognised. Try again.")
                    }
                }
            }

            input(InputType.text, classes = "form-control quest-code") {
                key = "code"
                attrs.maxLength = "8"
                attrs.placeholder = "Enter code"
                attrs.onChangeFunction = {
                    props.setError(false)
                }
                ref = inputRef
            }

            div("d-grid") {
                button(classes = "btn btn-success mb-2 mt-4", type = ButtonType.submit) {
                    +"Continue"
                }
            }
        }
    }
}
