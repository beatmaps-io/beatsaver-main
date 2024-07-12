package io.beatmaps.maps.testplay

import external.Axios
import external.IReCAPTCHA
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.FeedbackUpdate
import kotlinx.datetime.internal.JSJoda.Instant
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLTextAreaElement
import react.Props
import react.RBuilder
import react.RComponent
import react.RefObject
import react.State
import react.createRef
import react.dom.article
import react.dom.button
import react.dom.div
import react.dom.i
import react.dom.textarea
import react.setState

external interface NewFeedbackProps : Props {
    var hash: String
    var captcha: RefObject<IReCAPTCHA>
}

external interface NewFeedbackState : State {
    var loading: Boolean?
    var done: Boolean?
    var time: String?
    var text: String?
}

class NewFeedback : RComponent<NewFeedbackProps, NewFeedbackState>() {
    private val textareaRef = createRef<HTMLTextAreaElement>()

    override fun RBuilder.render() {
        if (state.done == true) {
            feedback {
                hash = props.hash
                name = ""
                feedback = state.text ?: ""
                time = state.time ?: ""
                isOwner = true
            }
        } else {
            article("card") {
                div("card-header icon bg-success") {
                    i("fas fa-plus") {}
                }
                div("card-body") {
                    textarea("10", classes = "form-control") {
                        attrs.disabled = state.loading == true
                        ref = textareaRef
                    }
                    div("float-end") {
                        button(classes = "btn btn-info mt-3") {
                            attrs.onClickFunction = {
                                val newText = textareaRef.current?.value ?: ""

                                setState {
                                    loading = true
                                }

                                props.captcha.current?.executeAsync()?.then {
                                    Axios.post<String>("${Config.apibase}/testplay/feedback", FeedbackUpdate(props.hash, newText, it), generateConfig<FeedbackUpdate, String>()).then({
                                        setState {
                                            done = true
                                            time = Instant.now().toString()
                                            text = newText
                                        }
                                    }) {
                                        setState {
                                            loading = false
                                        }
                                    }
                                }
                            }
                            attrs.disabled = state.loading == true
                            +"Leave feedback"
                        }
                    }
                }
            }
        }
    }
}

fun RBuilder.newFeedback(handler: NewFeedbackProps.() -> Unit) =
    child(NewFeedback::class) {
        this.attrs(handler)
    }
