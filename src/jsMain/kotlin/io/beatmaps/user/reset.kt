package io.beatmaps.user

import external.Axios
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.History
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.ResetRequest
import io.beatmaps.setPageTitle
import io.beatmaps.shared.form.errors
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.js.onSubmitFunction
import org.w3c.dom.HTMLInputElement
import react.Props
import react.dom.button
import react.dom.div
import react.dom.form
import react.dom.input
import react.fc
import react.router.useNavigate
import react.router.useParams
import react.useEffectOnce
import react.useRef
import react.useState

val resetPage = fc<Props> {
    val (errors, setErrors) = useState(listOf<String>())
    val (loading, setLoading) = useState(false)

    val passwordRef = useRef<HTMLInputElement>()
    val password2Ref = useRef<HTMLInputElement>()

    val params = useParams()
    val history = History(useNavigate())

    useEffectOnce {
        setPageTitle("Reset password")
    }

    div("login-form card border-dark") {
        div("card-header") {
            +"Reset password"
        }
        form(classes = "card-body") {
            attrs.onSubmitFunction = { ev ->
                ev.preventDefault()
                setLoading(true)

                Axios.post<ActionResponse>(
                    "${Config.apibase}/users/reset",
                    ResetRequest(
                        params["jwt"] ?: "",
                        passwordRef.current?.value ?: "",
                        password2Ref.current?.value ?: ""
                    ),
                    generateConfig<ResetRequest, ActionResponse>()
                ).then {
                    if (it.data.success) {
                        history.push("/login?reset")
                    } else {
                        setErrors(it.data.errors)
                        setLoading(false)
                    }
                }.catch {
                    // Cancelled request
                    setLoading(false)
                }
            }
            errors {
                attrs.errors = errors
            }
            input(type = InputType.password, classes = "form-control") {
                key = "password"
                ref = passwordRef
                attrs.placeholder = "Password"
                attrs.required = true
                attrs.autoFocus = true
                attrs.attributes["autocomplete"] = "new-password"
            }
            input(type = InputType.password, classes = "form-control") {
                key = "password2"
                ref = password2Ref
                attrs.placeholder = "Repeat Password"
                attrs.required = true
                attrs.attributes["autocomplete"] = "new-password"
            }
            div("d-grid") {
                button(classes = "btn btn-success", type = ButtonType.submit) {
                    attrs.disabled = loading
                    +"Reset password"
                }
            }
        }
    }
}
