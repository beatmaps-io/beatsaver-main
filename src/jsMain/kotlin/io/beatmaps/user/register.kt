package io.beatmaps.user

import external.Axios
import external.ReCAPTCHA
import external.generateConfig
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.RegisterRequest
import io.beatmaps.setPageTitle
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.id
import kotlinx.html.js.onSubmitFunction
import org.w3c.dom.HTMLInputElement
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
import react.createRef
import react.dom.a
import react.dom.button
import react.dom.div
import react.dom.form
import react.dom.i
import react.dom.input
import react.dom.jsStyle
import react.dom.p
import react.router.dom.routeLink
import react.setState

external interface SignupPageState : RState {
    var errors: List<String>
    var loading: Boolean
    var complete: Boolean
}

@JsExport
class SignupPage : RComponent<RProps, SignupPageState>() {
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
                                "/api/users/register",
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
                    input(type = InputType.email, classes = "form-control") {
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
                    button(classes = "btn btn-success btn-block", type = ButtonType.submit) {
                        attrs.disabled = state.loading
                        i("fas fa-user-plus") {}
                        +" Register"
                    }
                    routeLink("/login", className = "login_back") {
                        +"< Back"
                    }
                }
            }
        }

        ReCAPTCHA.default {
            attrs.sitekey = "6LdMpxUaAAAAAA6a3Fb2BOLQk9KO8wCSZ-a_YIaH"
            attrs.size = "invisible"
            ref = captchaRef
        }
    }
}

fun RBuilder.signupPage(handler: RProps.() -> Unit): ReactElement {
    return child(SignupPage::class) {
        this.attrs(handler)
    }
}
