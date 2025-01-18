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
import web.html.HTMLInputElement
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
        className = ClassName("login-form card border-dark")
        div {
            className = ClassName("card-header")
            +"Reset password"
        }
        form {
            className = ClassName("card-body")

            onSubmit = { ev ->
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
                this.errors = errors
            }
            input {
                type = InputType.password
                className = ClassName("form-control")
                key = "password"
                ref = passwordRef
                placeholder = "Password"
                required = true
                autoFocus = true
                autoComplete = AutoFillNormalField.newPassword
            }
            input {
                type = InputType.password
                className = ClassName("form-control")
                key = "password2"
                ref = password2Ref
                placeholder = "Repeat Password"
                required = true
                autoComplete = AutoFillNormalField.newPassword
            }
            div {
                className = ClassName("d-grid")
                button {
                    className = ClassName("btn btn-success")
                    type = ButtonType.submit

                    disabled = loading
                    +"Reset password"
                }
            }
        }
    }
}
