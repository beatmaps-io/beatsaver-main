package io.beatmaps.user

import external.Axios
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.History
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.ChangeEmailRequest
import io.beatmaps.setPageTitle
import io.beatmaps.shared.form.errors
import io.beatmaps.util.fcmemo
import io.beatmaps.util.parseJwt
import kotlinx.serialization.json.jsonPrimitive
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.form
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
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

val changeEmailPage = fcmemo<Props>("changeEmailPage") {
    val (loading, setLoading) = useState(false)
    val (errors, setErrors) = useState(emptyList<String>())
    val passwordRef = useRef<HTMLInputElement>()
    val params = useParams()
    val history = History(useNavigate())

    val (parsedJwt, _) = useState(parseJwt(params["jwt"] ?: ""))

    useEffectOnce {
        setPageTitle("Change Email")
    }

    div {
        className = ClassName("login-form card border-dark")
        div {
            className = ClassName("card-header")
            +"Change email"
        }
        form {
            className = ClassName("card-body pt-2")

            onSubmit = { ev ->
                ev.preventDefault()

                setLoading(true)

                Axios.post<ActionResponse>(
                    "${Config.apibase}/users/change-email",
                    ChangeEmailRequest(
                        params["jwt"] ?: "",
                        passwordRef.current?.value ?: ""
                    ),
                    generateConfig<ChangeEmailRequest, ActionResponse>()
                ).then {
                    if (it.data.success) {
                        history.push("/login?email")
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
            label {
                className = ClassName("form-label float-start mt-2")
                htmlFor = "email"
                +"New email"
            }
            input {
                type = InputType.email
                className = ClassName("form-control")
                key = "email"
                id = "email"
                disabled = true
                value = parsedJwt["email"]?.jsonPrimitive?.content ?: ""
            }
            if (parsedJwt["action"]?.jsonPrimitive?.content != "reclaim") {
                label {
                    className = ClassName("form-label float-start mt-3")
                    htmlFor = "password"
                    +"Verify password"
                }
                input {
                    type = InputType.password
                    className = ClassName("form-control")
                    key = "password"
                    ref = passwordRef
                    id = "password"
                    placeholder = "************"
                    required = true
                    autoFocus = true
                    autoComplete = AutoFillNormalField.newPassword
                }
            }
            div {
                className = ClassName("d-grid")
                button {
                    className = ClassName("btn btn-success")
                    type = ButtonType.submit

                    disabled = loading
                    +"Change email"
                }
            }
        }
    }
}
