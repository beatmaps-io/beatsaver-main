package io.beatmaps.maps.testplay

import external.Axios
import external.TimeAgo
import external.generateConfig
import io.beatmaps.api.FeedbackUpdate
import io.beatmaps.common.Config
import io.beatmaps.maps.textToContent
import kotlinx.datetime.internal.JSJoda.Instant
import kotlinx.html.TEXTAREA
import kotlinx.html.js.onClickFunction
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
import react.createRef
import react.dom.article
import react.dom.button
import react.dom.div
import react.dom.h3
import react.dom.i
import react.dom.small
import react.dom.textarea
import react.setState

external interface FeedbackProps : RProps {
    var hash: String
    var name: String
    var feedback: String
    var time: String
    var isOwner: Boolean
}

data class FeedbackState(var editing: Boolean = false, var loading: Boolean = false, var text: String = "", var time: String = "") : RState

@JsExport
class Feedback : RComponent<FeedbackProps, FeedbackState>() {
    private val textareaRef = createRef<TEXTAREA>()

    init {
        state = FeedbackState()
    }

    override fun componentWillMount() {
        setState {
            text = props.feedback
            time = props.time
        }
    }

    override fun RBuilder.render() {
        article("card border-primary") {
            div("card-header icon bg-primary") {
                i("fas fa-comments") {}
            }
            div("card-header") {
                if (props.isOwner) {
                    div("float-end") {
                        if (state.editing) {
                            button(classes = "btn btn-success m-1") {
                                attrs.onClickFunction = {
                                    val newText = textareaRef.current?.asDynamic().value as String

                                    setState {
                                        loading = true
                                    }

                                    Axios.post<String>("${Config.apibase}/testplay/feedback", FeedbackUpdate(props.hash, newText), generateConfig<FeedbackUpdate, String>()).then({
                                        setState {
                                            text = newText
                                            time = Instant.now().toString()
                                            editing = false
                                            loading = false
                                        }
                                    }) {
                                        setState {
                                            loading = false
                                        }
                                    }
                                }
                                attrs.disabled = state.loading
                                +"Save"
                            }
                        }
                        button(classes = "btn btn-info m-1") {
                            attrs.onClickFunction = {
                                setState {
                                    editing = !state.editing
                                }
                            }
                            attrs.disabled = state.loading
                            +(if (state.editing) "Cancel" else "Edit")
                        }
                    }
                }
                h3 {
                    if (props.isOwner) {
                        +"Your feedback"
                    } else {
                        +"${props.name}'s feedback"
                    }
                }
                small {
                    +props.hash
                }
            }
            div("card-body") {
                if (state.editing) {
                    textarea("10", classes = "form-control") {
                        ref = textareaRef
                        attrs.disabled = state.loading
                        +state.text
                    }
                } else {
                    textToContent(state.text)
                }
            }
            div("card-footer") {
                small {
                    TimeAgo.default {
                        key = state.time
                        attrs.date = state.time
                    }
                }
            }
        }
    }
}

fun RBuilder.feedback(handler: FeedbackProps.() -> Unit): ReactElement {
    return child(Feedback::class) {
        this.attrs(handler)
    }
}
