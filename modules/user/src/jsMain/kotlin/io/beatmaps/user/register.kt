package io.beatmaps.user

import external.Axios
import external.generateConfig
import external.routeLink
import io.beatmaps.Config
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.RegisterRequest
import io.beatmaps.captcha.ICaptchaHandler
import io.beatmaps.captcha.captcha
import io.beatmaps.setPageTitle
import io.beatmaps.shared.form.errors
import io.beatmaps.util.fcmemo
import org.w3c.dom.HTMLInputElement
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.form
import react.dom.html.ReactHTML.hr
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.small
import react.useEffectOnce
import react.useRef
import react.useState
import web.autofill.AutoFillNormalField
import web.cssom.ClassName
import web.html.ButtonType
import web.html.InputType

val signupPage = fcmemo<Props>("signupPage") {
    val (errors, setErrors) = useState(emptyList<String>())
    val (loading, setLoading) = useState(false)
    val (complete, setComplete) = useState(false)

    val usernameRef = useRef<HTMLInputElement>()
    val emailRef = useRef<HTMLInputElement>()
    val passwordRef = useRef<HTMLInputElement>()
    val password2Ref = useRef<HTMLInputElement>()

    val captchaRef = useRef<ICaptchaHandler>()

    useEffectOnce {
        setPageTitle("Register")
    }

    div {
        attrs.className = ClassName("login-form card border-dark")
        div {
            attrs.className = ClassName("card-header")
            +"Register"
        }
        form {
            attrs.className = ClassName("card-body")

            if (complete) {
                p {
                    +"Registration successful."
                }
                p {
                    +"Please check your email to finish setting up your account."
                }
            } else {
                attrs.onSubmit = { ev ->
                    ev.preventDefault()
                    setLoading(true)

                    captchaRef.current?.execute()?.then { captcha ->
                        captchaRef.current?.reset()

                        Axios.post<ActionResponse>(
                            "${Config.apibase}/users/register",
                            RegisterRequest(
                                captcha,
                                usernameRef.current?.value ?: "",
                                emailRef.current?.value ?: "",
                                passwordRef.current?.value ?: "",
                                password2Ref.current?.value ?: ""
                            ),
                            generateConfig<RegisterRequest, ActionResponse>()
                        ).then {
                            setComplete(it.data.success)
                            setErrors(it.data.errors)
                        }
                    }?.catch {
                        // Cancelled request or bad captcha
                        setErrors(listOfNotNull(it.message))
                    }?.finally {
                        setLoading(false)
                    }
                }
                errors {
                    attrs.errors = errors
                }
                input {
                    attrs.type = InputType.text
                    attrs.className = ClassName("form-control")
                    key = "username"
                    ref = usernameRef
                    attrs.id = "username"
                    attrs.placeholder = "Username"
                    attrs.required = true
                    attrs.autoFocus = true
                }
                small {
                    attrs.className = ClassName("d-block form-text text-muted mb-3 text-start")
                    +"Can only contain letters, numbers and ' . ', ' _ ', ' - ', no spaces"
                }
                input {
                    attrs.type = InputType.email
                    attrs.className = ClassName("form-control mb-3")
                    key = "email"
                    ref = emailRef
                    attrs.id = "email"
                    attrs.name = "email"
                    attrs.placeholder = "Email"
                    attrs.required = true
                }
                input {
                    attrs.type = InputType.password
                    attrs.className = ClassName("form-control")
                    key = "password"
                    ref = passwordRef
                    attrs.id = "password"
                    attrs.placeholder = "Password"
                    attrs.required = true
                    attrs.autoComplete = AutoFillNormalField.newPassword
                }
                input {
                    attrs.type = InputType.password
                    attrs.className = ClassName("form-control")
                    key = "password2"
                    ref = password2Ref
                    attrs.id = "password2"
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
                        i {
                            attrs.className = ClassName("fas fa-user-plus")
                        }
                        +" Register"
                    }
                }
                hr {}
                small {
                    +"By clicking Register, you agree to our "
                    a {
                        attrs.href = "/policy/tos"
                        +"Terms of Service"
                    }
                    +"."
                }
                hr {}
                routeLink("/login", className = "login_back") {
                    +"< Back"
                }
            }
        }
    }

    captcha {
        key = "captcha"
        attrs.captchaRef = captchaRef
        attrs.page = "register"
    }
}
