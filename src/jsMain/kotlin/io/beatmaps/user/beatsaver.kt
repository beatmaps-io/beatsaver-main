package io.beatmaps.user

import Axios
import generateConfig
import io.beatmaps.api.BeatsaverLink
import io.beatmaps.api.BeatsaverLinkReq
import io.beatmaps.setPageTitle
import kotlinx.browser.window
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.id
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLInputElement
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.createRef
import react.dom.*
import react.router.dom.RouteResultHistory
import react.setState

external interface BeatsaverPageProps : RProps {
    var history: RouteResultHistory
}

data class BeatsaverPageState(var failed: Boolean = false, var loading: Boolean = false) : RState

@JsExport
class BeatsaverPage : RComponent<BeatsaverPageProps, BeatsaverPageState>() {
    private val inputRef = createRef<HTMLInputElement>()
    private val passwordRef = createRef<HTMLInputElement>()
    private val oldNameRef = createRef<HTMLInputElement>()

    init {
        state = BeatsaverPageState()
    }

    override fun componentDidMount() {
        setPageTitle("Link Beatsaver")

        loadState()
    }

    private fun loadState() {
        setState {
            loading = true
        }

        Axios.get<BeatsaverLink>(
            "/api/users/beatsaver",
            generateConfig<String, BeatsaverLink>()
        ).then {
            val link = it.data

            if (link.linked) {
                props.history.push("/profile")
            } else {
                setState {
                    loading = false
                }
            }
        }.catch {
            // Cancelled request
        }
    }

    override fun RBuilder.render() {
        div("jumbotron") {
            h1 {
                +"Link beatsaver account"
            }
            p("mb-5") {
                +"To link your account enter your old login details:"
            }
            if (state.failed) {
                div("alert alert-danger col-7") {
                    h5 {
                        +"Failed to link accounts!"
                    }
                }
            }
            fieldset("col-5") {
                div("form-group") {
                    label {
                        attrs.htmlFor = "name"
                        +"Beatsaver Account name or id"
                    }
                    input(InputType.text, classes = "form-control") {
                        attrs.id = "name"
                        ref = inputRef
                    }
                }
                div("form-group") {
                    label {
                        attrs.htmlFor = "pwd"
                        +"Old Beatsaver Password"
                    }
                    input(InputType.password, classes = "form-control") {
                        attrs.id = "pwd"
                        ref = passwordRef
                    }
                }
                div("form-group") {
                    div("form-check") {
                        input(InputType.checkBox, classes = "form-check-input") {
                            attrs.id = "oldName"
                            ref = oldNameRef
                            attrs.checked = true
                        }
                        label("form-check-label") {
                            attrs.htmlFor = "oldName"
                            +"Use old beatsaver name as username"
                        }
                    }
                }
                button(type = ButtonType.submit, classes = "btn btn-primary") {
                    attrs.onClickFunction = {
                        val usr = inputRef.current?.value ?: ""
                        val pwd = passwordRef.current?.value ?: ""
                        val useOldName = oldNameRef.current?.checked ?: true

                        setState {
                            loading = true
                        }

                        Axios.post<BeatsaverLink>(
                            "/api/users/beatsaver",
                            BeatsaverLinkReq(usr, pwd, useOldName),
                            generateConfig<BeatsaverLinkReq, BeatsaverLink>()
                        ).then {
                            val link = it.data

                            if (link.linked) {
                                // Force a refresh as session has probably changed and needs to update nav
                                window.location.href = "/profile"
                            } else {
                                setState {
                                    failed = true
                                    loading = false
                                }
                            }
                        }.catch {
                            // Cancelled request
                            setState {
                                failed = true
                                loading = false
                            }
                        }
                    }
                    attrs.disabled = state.loading
                    +"Link"
                }
            }
        }
    }
}
