package io.beatmaps.user

import external.Axios
import external.ReCAPTCHA
import external.generateConfig
import external.recaptcha
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.ForgotRequest
import io.beatmaps.common.Config
import io.beatmaps.setPageTitle
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.js.onSubmitFunction
import org.w3c.dom.HTMLInputElement
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
import react.createRef
import react.dom.button
import react.dom.div
import react.dom.form
import react.dom.input
import react.dom.jsStyle
import react.dom.p
import react.router.dom.routeLink
import react.setState

external interface ForgotPageState : RState {
    var errors: List<String>
    var loading: Boolean
    var complete: Boolean
}

class ForgotPage : RComponent<RProps, ForgotPageState>() {
    private val emailRef = createRef<HTMLInputElement>()
    private val captchaRef = createRef<ReCAPTCHA>()

    override fun componentWillMount() {
        setState {
            errors = listOf()
            loading = false
            complete = false
        }
    }

    override fun componentDidMount() {
        setPageTitle("Forgot password")
    }

    override fun RBuilder.render() {
        div("login-form card border-dark") {
            div("card-header") {
                +"Reset password"
            }
            form(classes = "card-body") {
                if (state.complete) {
                    p {
                        +"We've sent you an email with a password reset link."
                    }
                } else {
                    attrs.onSubmitFunction = { ev ->
                        ev.preventDefault()

                        setState {
                            loading = true
                        }

                        captchaRef.current?.executeAsync()?.then { captcha ->
                            Axios.post<ActionResponse>(
                                "${Config.apibase}/users/forgot",
                                ForgotRequest(
                                    captcha,
                                    emailRef.current?.value ?: "",
                                ),
                                generateConfig<ForgotRequest, ActionResponse>()
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
                    input(type = InputType.email, classes = "form-control") {
                        key = "email"
                        ref = emailRef
                        attrs.placeholder = "Email"
                        attrs.required = true
                        attrs.autoFocus = true
                    }
                    div("d-grid") {
                        button(classes = "btn btn-success", type = ButtonType.submit) {
                            attrs.disabled = state.loading
                            +"Reset password"
                        }
                    }
                    routeLink("/login", className = "login_back") {
                        +"< Back"
                    }
                }
            }
        }

        recaptcha(captchaRef)
    }
}

fun RBuilder.forgotPage(handler: RProps.() -> Unit): ReactElement {
    return child(ForgotPage::class) {
        this.attrs(handler)
    }
}
