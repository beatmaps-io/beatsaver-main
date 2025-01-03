package io.beatmaps.user

import external.Axios
import external.ICaptchaHandler
import external.captcha
import external.generateConfig
import external.routeLink
import io.beatmaps.Config
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.RegisterRequest
import io.beatmaps.setPageTitle
import io.beatmaps.shared.form.errors
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.id
import kotlinx.html.js.onSubmitFunction
import org.w3c.dom.HTMLInputElement
import react.Props
import react.dom.a
import react.dom.button
import react.dom.div
import react.dom.form
import react.dom.hr
import react.dom.i
import react.dom.input
import react.dom.p
import react.dom.small
import react.fc
import react.useEffectOnce
import react.useRef
import react.useState

val signupPage = fc<Props> {
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

    div("login-form card border-dark") {
        div("card-header") {
            +"Register"
        }
        form(classes = "card-body") {
            if (complete) {
                p {
                    +"Registration successful."
                }
                p {
                    +"Please check your email to finish setting up your account."
                }
            } else {
                attrs.onSubmitFunction = { ev ->
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
                input(type = InputType.text, classes = "form-control") {
                    key = "username"
                    ref = usernameRef
                    attrs.id = "username"
                    attrs.placeholder = "Username"
                    attrs.required = true
                    attrs.autoFocus = true
                }
                small("d-block form-text text-muted mb-3 text-start") {
                    +"Can only contain letters, numbers and ' . ', ' _ ', ' - ', no spaces"
                }
                input(type = InputType.email, classes = "form-control mb-3") {
                    key = "email"
                    ref = emailRef
                    attrs.id = "email"
                    attrs.name = "email"
                    attrs.placeholder = "Email"
                    attrs.required = true
                }
                input(type = InputType.password, classes = "form-control") {
                    key = "password"
                    ref = passwordRef
                    attrs.id = "password"
                    attrs.placeholder = "Password"
                    attrs.required = true
                    attrs.attributes["autocomplete"] = "new-password"
                }
                input(type = InputType.password, classes = "form-control") {
                    key = "password2"
                    ref = password2Ref
                    attrs.id = "password2"
                    attrs.placeholder = "Repeat Password"
                    attrs.required = true
                    attrs.attributes["autocomplete"] = "new-password"
                }
                div("d-grid") {
                    button(classes = "btn btn-success", type = ButtonType.submit) {
                        attrs.disabled = loading
                        i("fas fa-user-plus") {}
                        +" Register"
                    }
                }
                hr {}
                small {
                    +"By clicking Register, you agree to our "
                    a("/policy/tos") {
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

    captcha(captchaRef)
}
