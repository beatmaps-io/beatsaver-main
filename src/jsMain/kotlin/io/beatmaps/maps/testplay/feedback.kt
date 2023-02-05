package io.beatmaps.maps.testplay

import external.Axios
import external.TimeAgo
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.FeedbackUpdate
import io.beatmaps.util.textToContent
import kotlinx.datetime.internal.JSJoda.Instant
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLTextAreaElement
import react.Props
import react.RBuilder
import react.RComponent
import react.State
import react.createRef
import react.dom.article
import react.dom.button
import react.dom.div
import react.dom.h3
import react.dom.i
import react.dom.small
import react.dom.textarea
import react.setState

external interface FeedbackProps : Props {
    var hash: String
    var name: String
    var feedback: String
    var time: String
    var isOwner: Boolean
}

external interface FeedbackState : State {
    var editing: Boolean?
    var loading: Boolean?
    var text: String?
    var time: String?
}

class Feedback : RComponent<FeedbackProps, FeedbackState>() {
    private val textareaRef = createRef<HTMLTextAreaElement>()

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
                        if (state.editing == true) {
                            button(classes = "btn btn-success m-1") {
                                attrs.onClickFunction = {
                                    val newText = textareaRef.current?.value ?: ""

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
                                attrs.disabled = state.loading == true
                                +"Save"
                            }
                        }
                        button(classes = "btn btn-info m-1") {
                            attrs.onClickFunction = {
                                setState {
                                    editing = state.editing != true
                                }
                            }
                            attrs.disabled = state.loading == true
                            +(if (state.editing == true) "Cancel" else "Edit")
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
                if (state.editing == true) {
                    textarea("10", classes = "form-control") {
                        ref = textareaRef
                        attrs.disabled = state.loading == true
                        +(state.text ?: "")
                    }
                } else {
                    textToContent(state.text ?: "")
                }
            }
            div("card-footer") {
                small {
                    TimeAgo.default {
                        key = state.time
                        attrs.date = state.time ?: ""
                    }
                }
            }
        }
    }
}

fun RBuilder.feedback(handler: FeedbackProps.() -> Unit) =
    child(Feedback::class) {
        this.attrs(handler)
    }
