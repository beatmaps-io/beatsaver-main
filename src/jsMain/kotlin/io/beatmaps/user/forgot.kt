package io.beatmaps.user

import external.Axios
import external.generateConfig
import external.routeLink
import io.beatmaps.Config
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.ForgotRequest
import io.beatmaps.captcha.ICaptchaHandler
import io.beatmaps.captcha.captcha
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
import react.dom.p
import react.fc
import react.useEffectOnce
import react.useRef
import react.useState

val forgotPage = fc<Props>("forgotPage") {
    val (complete, setComplete) = useState(false)
    val (loading, setLoading) = useState(false)
    val (errors, setErrors) = useState(emptyList<String>())

    val emailRef = useRef<HTMLInputElement>()
    val captchaRef = useRef<ICaptchaHandler>()

    useEffectOnce {
        setPageTitle("Forgot password")
    }

    div("login-form card border-dark") {
        div("card-header") {
            +"Reset password"
        }
        form(classes = "card-body") {
            if (complete) {
                p {
                    +"We've sent you an email with a password reset link."
                }
            } else {
                attrs.onSubmitFunction = { ev ->
                    ev.preventDefault()
                    setLoading(true)

                    captchaRef.current?.execute()?.then { captcha ->
                        Axios.post<ActionResponse>(
                            "${Config.apibase}/users/forgot",
                            ForgotRequest(
                                captcha,
                                emailRef.current?.value ?: ""
                            ),
                            generateConfig<ForgotRequest, ActionResponse>()
                        ).then {
                            if (it.data.success) {
                                setComplete(true)
                            } else {
                                captchaRef.current?.reset()
                                setErrors(it.data.errors)
                                setLoading(false)
                            }
                        }
                    }?.catch {
                        // Cancelled request or bad captcha
                        setErrors(listOfNotNull(it.message))
                        setLoading(false)
                    }
                }
                errors {
                    attrs.errors = errors
                }
                input(type = InputType.email, classes = "form-control") {
                    key = "email"
                    ref = emailRef
                    attrs.placeholder = "Email"
                    attrs.required = true
                    attrs.autoFocus = true
                }
                div("d-grid") {
                    button(classes = "btn btn-success", type = ButtonType.submit) {
                        attrs.disabled = loading
                        +"Reset password"
                    }
                }
                routeLink("/login", className = "login_back") {
                    +"< Back"
                }
            }
        }
    }

    captcha {
        attrs.captchaRef = captchaRef
        attrs.page = "forgot"
    }
}
