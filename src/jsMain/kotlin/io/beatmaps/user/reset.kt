package io.beatmaps.user

import external.Axios
import external.generateConfig
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.ResetRequest
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
import react.router.dom.RouteResultHistory
import react.setState

external interface ResetPageProps : RProps {
    var jwt: String
    var history: RouteResultHistory
}

external interface ResetPageState : RState {
    var errors: List<String>
    var loading: Boolean
}

@JsExport
class ResetPage : RComponent<ResetPageProps, ResetPageState>() {
    private val passwordRef = createRef<HTMLInputElement>()
    private val password2Ref = createRef<HTMLInputElement>()

    override fun componentWillMount() {
        setState {
            errors = listOf()
            loading = false
        }
    }

    override fun componentDidMount() {
        setPageTitle("Reset password")
    }

    override fun RBuilder.render() {
        div("login-form card border-dark") {
            div("card-header") {
                +"Reset password"
            }
            form(classes = "card-body") {
                attrs.onSubmitFunction = { ev ->
                    ev.preventDefault()

                    setState {
                        loading = true
                    }

                    Axios.post<ActionResponse>(
                        "${Config.apibase}/users/reset",
                        ResetRequest(
                            props.jwt,
                            passwordRef.current?.value ?: "",
                            password2Ref.current?.value ?: "",
                        ),
                        generateConfig<ResetRequest, ActionResponse>()
                    ).then {
                        if (it.data.success) {
                            props.history.push("/login?reset")
                        } else {
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
                state.errors.forEach {
                    div("invalid-feedback") {
                        attrs.jsStyle {
                            display = "block"
                        }
                        +it
                    }
                }
                input(type = InputType.password, classes = "form-control") {
                    key = "password"
                    ref = passwordRef
                    attrs.placeholder = "Password"
                    attrs.required = true
                    attrs.autoFocus = true
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
                    +"Reset password"
                }
            }
        }
    }
}

fun RBuilder.resetPage(handler: ResetPageProps.() -> Unit): ReactElement {
    return child(ResetPage::class) {
        this.attrs(handler)
    }
}
