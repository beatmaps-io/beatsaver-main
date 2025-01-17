package io.beatmaps.user

import external.Axios
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.History
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.ResetRequest
import io.beatmaps.setPageTitle
import io.beatmaps.shared.form.errors
import io.beatmaps.util.fcmemo
import org.w3c.dom.HTMLInputElement
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.form
import react.dom.html.ReactHTML.input
import react.router.useNavigate
import react.router.useParams
import react.useEffectOnce
import react.useRef
import react.useState
import web.autofill.AutoFillNormalField
import web.cssom.ClassName
import web.html.ButtonType
import web.html.InputType

val resetPage = fcmemo<Props>("resetPage") {
    val (errors, setErrors) = useState(emptyList<String>())
    val (loading, setLoading) = useState(false)

    val passwordRef = useRef<HTMLInputElement>()
    val password2Ref = useRef<HTMLInputElement>()

    val params = useParams()
    val history = History(useNavigate())

    useEffectOnce {
        setPageTitle("Reset password")
    }

    div {
        attrs.className = ClassName("login-form card border-dark")
        div {
            attrs.className = ClassName("card-header")
            +"Reset password"
        }
        form {
            attrs.className = ClassName("card-body")

            attrs.onSubmit = { ev ->
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
            input {
                attrs.type = InputType.password
                attrs.className = ClassName("form-control")
                key = "password"
                ref = passwordRef
                attrs.placeholder = "Password"
                attrs.required = true
                attrs.autoFocus = true
                attrs.autoComplete = AutoFillNormalField.newPassword
            }
            input {
                attrs.type = InputType.password
                attrs.className = ClassName("form-control")
                key = "password2"
                ref = password2Ref
                attrs.placeholder = "Repeat Password"
                attrs.required = true
                attrs.autoComplete = AutoFillNormalField.newPassword
            }
            div {
                attrs.className = ClassName("d-grid")
                button {
                    attrs.className = ClassName("btn btn-success")
                    attrs.type = ButtonType.submit

                    attrs.disabled = loading
                    +"Reset password"
                }
            }
        }
    }
}
