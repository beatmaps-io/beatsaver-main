package io.beatmaps.quest

import external.Axios
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.QuestCode
import io.beatmaps.api.QuestCodeResponse
import io.beatmaps.shared.form.errors
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.url.URLSearchParams
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.form
import react.dom.html.ReactHTML.input
import react.fc
import react.router.useLocation
import react.useEffect
import react.useRef
import web.cssom.ClassName
import web.html.ButtonType
import web.html.InputType

external interface QuestCodeProps : Props {
    var deviceCodeCallback: (String, QuestCodeResponse) -> Unit
    var setError: (Boolean) -> Unit
    var error: Boolean
}

val questCode = fc<QuestCodeProps>("questCode") { props ->
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

    div {
        attrs.className = ClassName("login-form card border-dark")
        div {
            attrs.className = ClassName("card-header")
            +"Enter code"
        }
        form {
            attrs.className = ClassName("card-body")
            attrs.onSubmit = { ev ->
                ev.preventDefault()
                onSubmit()
            }

            div {
                attrs.className = ClassName("quest-errors")
                if (props.error) {
                    errors {
                        attrs.errors = listOf("Code not recognised. Try again.")
                    }
                }
            }

            input {
                key = "code"
                attrs.type = InputType.text
                attrs.className = ClassName("form-control quest-code")
                attrs.maxLength = 8
                attrs.placeholder = "Enter code"
                attrs.onChange = {
                    props.setError(false)
                }
                ref = inputRef
            }

            div {
                attrs.className = ClassName("d-grid")
                button {
                    attrs.className = ClassName("btn btn-success mb-2 mt-4")
                    attrs.type = ButtonType.submit
                    +"Continue"
                }
            }
        }
    }
}
