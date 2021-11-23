package io.beatmaps.maps.testplay

import external.Axios
import external.ReCAPTCHA
import external.generateConfig
import io.beatmaps.api.FeedbackUpdate
import io.beatmaps.common.Config
import kotlinx.datetime.internal.JSJoda.Instant
import kotlinx.html.TEXTAREA
import kotlinx.html.js.onClickFunction
import react.RBuilder
import react.RComponent
import react.RProps
import react.RReadableRef
import react.RState
import react.ReactElement
import react.createRef
import react.dom.article
import react.dom.button
import react.dom.div
import react.dom.i
import react.dom.textarea
import react.setState

external interface NewFeedbackProps : RProps {
    var hash: String
    var captcha: RReadableRef<ReCAPTCHA>
}

data class NewFeedbackState(var loading: Boolean = false, var done: Boolean = false, var time: String = "", var text: String = "") : RState

@JsExport
class NewFeedback : RComponent<NewFeedbackProps, NewFeedbackState>() {
    private val textareaRef = createRef<TEXTAREA>()

    init {
        state = NewFeedbackState()
    }

    override fun RBuilder.render() {
        if (state.done) {
            feedback {
                hash = props.hash
                name = ""
                feedback = state.text
                time = state.time
                isOwner = true
            }
        } else {
            article("card") {
                div("card-header icon bg-success") {
                    i("fas fa-plus") {}
                }
                div("card-body") {
                    textarea("10", classes = "form-control") {
                        attrs.disabled = state.loading
                        ref = textareaRef
                    }
                    div("float-right") {
                        button(classes = "btn btn-info mt-3") {
                            attrs.onClickFunction = {
                                val newText = textareaRef.current?.asDynamic().value as String

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
                            attrs.disabled = state.loading
                            +"Leave feedback"
                        }
                    }
                }
            }
        }
    }
}

fun RBuilder.newFeedback(handler: NewFeedbackProps.() -> Unit): ReactElement {
    return child(NewFeedback::class) {
        this.attrs(handler)
    }
}
