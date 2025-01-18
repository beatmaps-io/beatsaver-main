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
import web.html.HTMLInputElement
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
        className = ClassName("login-form card border-dark")
        div {
            className = ClassName("card-header")
            +"Register"
        }
        form {
            className = ClassName("card-body")

            if (complete) {
                p {
                    +"Registration successful."
                }
                p {
                    +"Please check your email to finish setting up your account."
                }
            } else {
                onSubmit = { ev ->
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
                    this.errors = errors
                }
                input {
                    type = InputType.text
                    className = ClassName("form-control")
                    key = "username"
                    ref = usernameRef
                    id = "username"
                    placeholder = "Username"
                    required = true
                    autoFocus = true
                }
                small {
                    className = ClassName("d-block form-text text-muted mb-3 text-start")
                    +"Can only contain letters, numbers and ' . ', ' _ ', ' - ', no spaces"
                }
                input {
                    type = InputType.email
                    className = ClassName("form-control mb-3")
                    key = "email"
                    ref = emailRef
                    id = "email"
                    name = "email"
                    placeholder = "Email"
                    required = true
                }
                input {
                    type = InputType.password
                    className = ClassName("form-control")
                    key = "password"
                    ref = passwordRef
                    id = "password"
                    placeholder = "Password"
                    required = true
                    autoComplete = AutoFillNormalField.newPassword
                }
                input {
                    type = InputType.password
                    className = ClassName("form-control")
                    key = "password2"
                    ref = password2Ref
                    id = "password2"
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
                        i {
                            className = ClassName("fas fa-user-plus")
                        }
                        +" Register"
                    }
                }
                hr {}
                small {
                    +"By clicking Register, you agree to our "
                    a {
                        href = "/policy/tos"
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
        this.captchaRef = captchaRef
        page = "register"
    }
}
