package io.beatmaps.user

import external.Axios
import external.axiosGet
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.WithRouterProps
import io.beatmaps.api.AccountDetailReq
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.UserDetail
import io.beatmaps.common.json
import io.beatmaps.setPageTitle
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.js.onSubmitFunction
import kotlinx.serialization.decodeFromString
import org.w3c.dom.HTMLInputElement
import react.RBuilder
import react.RComponent
import react.State
import react.createRef
import react.dom.button
import react.dom.div
import react.dom.form
import react.dom.i
import react.dom.input
import react.dom.jsStyle
import react.dom.p
import react.dom.span
import react.setState

external interface PickUsernameProps : WithRouterProps

external interface PickUsernameState : State {
    var errors: List<String>?
    var loading: Boolean?
    var submitted: Boolean?
}

class PickUsernamePage : RComponent<PickUsernameProps, PickUsernameState>() {
    private val inputRef = createRef<HTMLInputElement>()

    override fun componentDidMount() {
        setPageTitle("Set username")

        loadState()
    }

    private fun loadState() {
        setState {
            loading = true
        }

        axiosGet<String>(
            "${Config.apibase}/users/me"
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
                        "${Config.apibase}/users/username",
                        AccountDetailReq(inputRef.current?.value ?: ""),
                        generateConfig<AccountDetailReq, ActionResponse>()
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
                p("text-start") {
                    attrs.jsStyle {
                        fontSize = "0.8rem"
                    }
                    +"Please pick a beatsaver username for your account. You will not be able to change this later."
                }
                p("text-start") {
                    attrs.jsStyle {
                        fontSize = "0.8rem"
                    }
                    +"Usernames must be made up of letters, numbers and any of "
                    span("badge badge-secondary") {
                        attrs.jsStyle {
                            fontSize = "0.8rem"
                        }
                        +". _ -"
                    }
                }
                state.errors?.forEach {
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
                div("d-grid") {
                    button(classes = "btn btn-success", type = ButtonType.submit) {
                        attrs.disabled = state.submitted == true
                        i("fas fa-check") {}
                        +" Continue"
                    }
                }
            }
        }
    }
}

fun RBuilder.pickUsernamePage(handler: PickUsernameProps.() -> Unit) =
    child(PickUsernamePage::class) {
        this.attrs(handler)
    }
