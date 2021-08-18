package io.beatmaps.user

import external.Axios
import external.axiosGet
import external.generateConfig
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.UserDetail
import io.beatmaps.api.UsernameReq
import io.beatmaps.common.json
import io.beatmaps.setPageTitle
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.js.onSubmitFunction
import kotlinx.serialization.decodeFromString
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
import react.dom.i
import react.dom.input
import react.dom.jsStyle
import react.dom.p
import react.dom.span
import react.router.dom.RouteResultHistory
import react.setState

external interface PickUsernameProps : RProps {
    var history: RouteResultHistory
}

external interface PickUsernameState : RState {
    var errors: List<String>
    var loading: Boolean
    var submitted: Boolean
}

@JsExport
class PickUsernamePage : RComponent<PickUsernameProps, PickUsernameState>() {
    private val inputRef = createRef<HTMLInputElement>()

    override fun componentWillMount() {
        setState {
            errors = listOf()
            loading = false
            submitted = false
        }
    }

    override fun componentDidMount() {
        setPageTitle("Set username")

        loadState()
    }

    private fun loadState() {
        setState {
            loading = true
        }

        axiosGet<String>(
            "/api/users/me"
        ).then {
            // Decode is here so that 401 actually passes to error handler
            val data = json.decodeFromString<UserDetail>(it.data)

            if (data.uniqueSet) {
                props.history.push("/")
            } else {
                setState {
                    loading = false
                }
                val re = Regex("[^._\\-A-Za-z0-9]")
                inputRef.current?.value = re.replace(data.name.replace(' ', '-'), "")
            }
        }.catch {
            if (it.asDynamic().response?.status == 401) {
                props.history.push("/login")
            }
            // Cancelled request
        }
    }

    override fun RBuilder.render() {
        div("login-form card border-dark") {
            div("card-header") {
                +"Pick a username"
            }
            form(classes = "card-body") {
                attrs.onSubmitFunction = { ev ->
                    ev.preventDefault()

                    Axios.post<ActionResponse>(
                        "/api/users/username",
                        UsernameReq(inputRef.current?.value ?: ""),
                        generateConfig<UsernameReq, ActionResponse>()
                    ).then {
                        if (it.data.success) {
                            props.history.push("/")
                        } else {
                            setState {
                                errors = it.data.errors
                                submitted = false
                            }
                        }
                    }.catch {
                        // Cancelled request
                        setState {
                            submitted = false
                        }
                    }
                }
                p("text-left") {
                    attrs.jsStyle {
                        fontSize = "0.8rem"
                    }
                    +"Please pick a beatsaver username for your account. You will not be able to change this later."
                }
                p("text-left") {
                    attrs.jsStyle {
                        fontSize = "0.8rem"
                    }
                    +"Usernames must be made up of letters, numbers and any of "
                    span("badge bg-secondary") {
                        attrs.jsStyle {
                            fontSize = "0.8rem"
                        }
                        +". _ -"
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
                    ref = inputRef
                    key = "username"
                    attrs.name = "username"
                    attrs.placeholder = "Username"
                    attrs.required = true
                    attrs.autoFocus = true
                }
                button(classes = "btn btn-success btn-block", type = ButtonType.submit) {
                    attrs.disabled = state.submitted
                    i("fas fa-check") {}
                    +" Continue"
                }
            }
        }
    }
}

fun RBuilder.pickUsernamePage(handler: PickUsernameProps.() -> Unit): ReactElement {
    return child(PickUsernamePage::class) {
        this.attrs(handler)
    }
}
