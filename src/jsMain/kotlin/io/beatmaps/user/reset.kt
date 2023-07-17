package io.beatmaps.user

import external.Axios
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.WithRouterProps
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.ResetRequest
import io.beatmaps.setPageTitle
import io.beatmaps.shared.errors
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.js.onSubmitFunction
import org.w3c.dom.HTMLInputElement
import react.RBuilder
import react.RComponent
import react.State
import react.createRef
import react.dom.button
import react.dom.div
import react.dom.form
import react.dom.input
import react.setState

external interface ResetPageProps : WithRouterProps

external interface ResetPageState : State {
    var errors: List<String>
    var loading: Boolean
}

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
                            props.params["jwt"] ?: "",
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
                errors {
                    attrs.errors = state.errors
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
                div("d-grid") {
                    button(classes = "btn btn-success", type = ButtonType.submit) {
                        attrs.disabled = state.loading
                        +"Reset password"
                    }
                }
            }
        }
    }
}

fun RBuilder.resetPage(handler: ResetPageProps.() -> Unit) =
    child(ResetPage::class) {
        this.attrs(handler)
    }
