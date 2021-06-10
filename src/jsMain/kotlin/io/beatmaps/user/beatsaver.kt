package io.beatmaps.user

import Axios
import AxiosRequestConfig
import generateConfig
import io.beatmaps.api.BeatsaverLink
import io.beatmaps.api.FeedbackUpdate
import io.beatmaps.setPageTitle
import kotlinx.browser.window
import kotlinx.html.ButtonType
import kotlinx.html.INPUT
import kotlinx.html.InputType
import kotlinx.html.js.onClickFunction
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
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

data class BeatsaverPageState(var failed: Boolean = false, var loading: Boolean = false, var hash: String = "") : RState

@JsExport
class BeatsaverPage : RComponent<BeatsaverPageProps, BeatsaverPageState>() {
    private val inputRef = createRef<HTMLInputElement>()

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
                    hash = link.md
                    loading = false
                }
            }
        }.catch {
            // Cancelled request
        }
    }

    override fun RBuilder.render() {
        main("container") {
            div("jumbotron") {
                h1 {
                    +"Link beatsaver account"
                }
                p("mb-5") {
                    +"To link your account edit the title or description of one your recent songs to include "
                    span("badge badge-light m-1") {
                        +state.hash
                    }
                }
                if (state.failed) {
                    div("alert alert-danger col-7") {
                        h5 {
                            +"Failed to link accounts!"
                        }
                        p {
                            +"Accounts will only link if you have uploaded maps"
                        }
                        p {
                            +"If this continues try using your beatsaver id e.g. '5ffdaabc6046050006231ed1' instead of your username"
                        }
                    }
                }
                fieldSet("col-5") {
                    div("form-group") {
                        label {
                            attrs.htmlFor = "name"
                            +"Beatsaver Account name or id"
                        }
                        input(InputType.text, classes = "form-control") {
                            ref = inputRef
                        }
                    }
                    button(type = ButtonType.submit, classes = "btn btn-primary") {
                        attrs.onClickFunction = {
                            val usr = inputRef.current?.value

                            setState {
                                loading = true
                            }

                            Axios.post<BeatsaverLink>(
                                "/api/users/beatsaver/$usr",
                                "",
                                generateConfig<String, BeatsaverLink>()
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
}