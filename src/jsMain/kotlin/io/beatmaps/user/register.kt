package io.beatmaps.user

import external.Axios
import external.ReCAPTCHA
import external.generateConfig
import external.recaptcha
import external.routeLink
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.RegisterRequest
import io.beatmaps.common.Config
import io.beatmaps.setPageTitle
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.js.onSubmitFunction
import org.w3c.dom.HTMLInputElement
import react.Props
import react.RBuilder
import react.RComponent
import react.State
import react.createRef
import react.dom.a
import react.dom.button
import react.dom.div
import react.dom.form
import react.dom.hr
import react.dom.i
import react.dom.input
import react.dom.jsStyle
import react.dom.p
import react.dom.small
import react.setState

external interface SignupPageState : State {
    var errors: List<String>
    var loading: Boolean
    var complete: Boolean
}

class SignupPage : RComponent<Props, SignupPageState>() {
    private val usernameRef = createRef<HTMLInputElement>()
    private val emailRef = createRef<HTMLInputElement>()
    private val passwordRef = createRef<HTMLInputElement>()
    private val password2Ref = createRef<HTMLInputElement>()

    private val captchaRef = createRef<ReCAPTCHA>()

    override fun componentWillMount() {
        setState {
            errors = listOf()
            loading = false
            complete = false
        }
    }

    override fun componentDidMount() {
        setPageTitle("Register")
    }

    override fun RBuilder.render() {
        div("login-form card border-dark") {
            div("card-header") {
                +"Register"
            }
            form(classes = "card-body") {
                if (state.complete) {
                    p {
                        +"Registration successful."
                    }
                    p {
                        +"Please check your email to finish setting up your account."
                    }
                } else {
                    attrs.onSubmitFunction = { ev ->
                        ev.preventDefault()

                        setState {
                            loading = true
                        }

                        captchaRef.current?.executeAsync()?.then { captcha ->
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
                                if (it.data.success) {
                                    setState {
                                        complete = true
                                    }
                                } else {
                                    captchaRef.current?.reset()
                                    setState {
                                        errors = it.data.errors
                                        loading = false
                                    }
                                }
                            }.catch {
                                // Cancelled request
                                setState {
                                    loading = false
                                }
                            }
                        }
                    }
                    state.errors.forEach {
                        div("invalid-feedback") {
                            attrs.jsStyle {
                                display = "block"
                            }
                            +it
                        }
                    }
                    input(type = InputType.text, classes = "form-control") {
                        key = "username"
                        ref = usernameRef
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
                        attrs.name = "email"
                        attrs.placeholder = "Email"
                        attrs.required = true
                    }
                    input(type = InputType.password, classes = "form-control") {
                        key = "password"
                        ref = passwordRef
                        attrs.placeholder = "Password"
                        attrs.required = true
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
                            attrs.disabled = state.loading
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

        recaptcha(captchaRef)
    }
}

fun RBuilder.signupPage(handler: Props.() -> Unit) =
    child(SignupPage::class) {
        this.attrs(handler)
    }
