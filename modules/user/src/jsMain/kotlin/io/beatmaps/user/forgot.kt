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
import io.beatmaps.util.fcmemo
import org.w3c.dom.HTMLInputElement
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.form
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.p
import react.useEffectOnce
import react.useRef
import react.useState
import web.cssom.ClassName
import web.html.ButtonType
import web.html.InputType

val forgotPage = fcmemo<Props>("forgotPage") {
    val (complete, setComplete) = useState(false)
    val (loading, setLoading) = useState(false)
    val (errors, setErrors) = useState(emptyList<String>())

    val emailRef = useRef<HTMLInputElement>()
    val captchaRef = useRef<ICaptchaHandler>()

    useEffectOnce {
        setPageTitle("Forgot password")
    }

    div {
        attrs.className = ClassName("login-form card border-dark")
        div {
            attrs.className = ClassName("card-header")
            +"Reset password"
        }
        form {
            attrs.className = ClassName("card-body")

            if (complete) {
                p {
                    +"We've sent you an email with a password reset link."
                }
            } else {
                attrs.onSubmit = { ev ->
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
                input {
                    attrs.type = InputType.email
                    attrs.className = ClassName("form-control")
                    key = "email"
                    ref = emailRef
                    attrs.placeholder = "Email"
                    attrs.required = true
                    attrs.autoFocus = true
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
                routeLink("/login", className = "login_back") {
                    +"< Back"
                }
            }
        }
    }

    captcha {
        key = "captcha"
        attrs.captchaRef = captchaRef
        attrs.page = "forgot"
    }
}
