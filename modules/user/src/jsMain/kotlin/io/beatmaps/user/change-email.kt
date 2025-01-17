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
import org.w3c.dom.HTMLInputElement
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
        attrs.className = ClassName("login-form card border-dark")
        div {
            attrs.className = ClassName("card-header")
            +"Change email"
        }
        form {
            attrs.className = ClassName("card-body pt-2")

            attrs.onSubmit = { ev ->
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
                attrs.errors = errors
            }
            label {
                attrs.className = ClassName("form-label float-start mt-2")
                attrs.htmlFor = "email"
                +"New email"
            }
            input {
                attrs.type = InputType.email
                attrs.className = ClassName("form-control")
                key = "email"
                attrs.id = "email"
                attrs.disabled = true
                attrs.value = parsedJwt["email"]?.jsonPrimitive?.content ?: ""
            }
            if (parsedJwt["action"]?.jsonPrimitive?.content != "reclaim") {
                label {
                    attrs.className = ClassName("form-label float-start mt-3")
                    attrs.htmlFor = "password"
                    +"Verify password"
                }
                input {
                    attrs.type = InputType.password
                    attrs.className = ClassName("form-control")
                    key = "password"
                    ref = passwordRef
                    attrs.id = "password"
                    attrs.placeholder = "************"
                    attrs.required = true
                    attrs.autoFocus = true
                    attrs.autoComplete = AutoFillNormalField.newPassword
                }
            }
            div {
                attrs.className = ClassName("d-grid")
                button {
                    attrs.className = ClassName("btn btn-success")
                    attrs.type = ButtonType.submit

                    attrs.disabled = loading
                    +"Change email"
                }
            }
        }
    }
}
