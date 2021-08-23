package io.beatmaps.user

import external.Axios
import external.axiosGet
import external.generateConfig
import io.beatmaps.api.BeatsaverLink
import io.beatmaps.api.BeatsaverLinkReq
import io.beatmaps.setPageTitle
import kotlinx.browser.window
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.id
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLInputElement
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.createRef
import react.dom.button
import react.dom.div
import react.dom.fieldset
import react.dom.h1
import react.dom.h5
import react.dom.input
import react.dom.label
import react.dom.p
import react.router.dom.RouteResultHistory
import react.setState

external interface BeatsaverPageProps : RProps {
    var history: RouteResultHistory
}

external interface BeatsaverPageState : RState {
    var failed: Boolean
    var loading: Boolean
    var oldName: Boolean
}

@JsExport
class BeatsaverPage : RComponent<BeatsaverPageProps, BeatsaverPageState>() {
    private val inputRef = createRef<HTMLInputElement>()
    private val passwordRef = createRef<HTMLInputElement>()

    override fun componentWillMount() {
        setState {
            failed = false
            loading = false
            oldName = true
        }
    }

    override fun componentDidMount() {
        setPageTitle("Link Beatsaver")

        loadState()
    }

    private fun loadState() {
        setState {
            loading = true
        }

        axiosGet<BeatsaverLink>(
            "/api/users/beatsaver"
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
                            attrs.onChangeFunction = {
                                setState {
                                    oldName = !state.oldName
                                }
                            }
                            attrs.id = "oldName"
                            attrs.checked = state.oldName
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
                        val useOldName = state.oldName

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
