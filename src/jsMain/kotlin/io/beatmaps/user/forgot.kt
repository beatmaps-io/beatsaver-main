package io.beatmaps.user

import external.Axios
import external.ReCAPTCHA
import external.generateConfig
import external.recaptcha
import external.routeLink
import io.beatmaps.Config
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.ForgotRequest
import io.beatmaps.setPageTitle
import io.beatmaps.shared.errors
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.js.onSubmitFunction
import org.w3c.dom.HTMLInputElement
import react.Props
import react.RBuilder
import react.RComponent
import react.State
import react.createRef
import react.dom.button
import react.dom.div
import react.dom.form
import react.dom.input
import react.dom.p
import react.setState

external interface ForgotPageState : State {
    var errors: List<String>
    var loading: Boolean
    var complete: Boolean
}

class ForgotPage : RComponent<Props, ForgotPageState>() {
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
                    errors {
                        attrs.errors = state.errors
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

fun RBuilder.forgotPage(handler: Props.() -> Unit) =
    child(ForgotPage::class) {
        this.attrs(handler)
    }
