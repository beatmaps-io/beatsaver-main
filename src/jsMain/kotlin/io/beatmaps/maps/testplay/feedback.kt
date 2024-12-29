package io.beatmaps.maps.testplay

import external.Axios
import external.TimeAgo
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.FeedbackUpdate
import io.beatmaps.util.textToContent
import kotlinx.datetime.Clock
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLTextAreaElement
import react.Props
import react.dom.button
import react.dom.div
import react.dom.h3
import react.dom.small
import react.dom.textarea
import react.fc
import react.useRef
import react.useState

external interface FeedbackProps : Props {
    var hash: String
    var name: String
    var feedback: String
    var time: String
    var isOwner: Boolean
}

val feedback = fc<FeedbackProps> { props ->
    val (editing, setEditing) = useState(false)
    val (loading, setLoading) = useState(false)
    val (text, setText) = useState(props.feedback)
    val (time, setTime) = useState(props.time)

    val textareaRef = useRef<HTMLTextAreaElement>()

    timelineEntry {
        attrs.icon = "fa-comments"
        attrs.color = "primary"
        attrs.headerCallback = TimelineEntrySectionRenderer {
            if (props.isOwner) {
                div("float-end") {
                    if (editing) {
                        button(classes = "btn btn-success m-1") {
                            attrs.onClickFunction = {
                                val newText = textareaRef.current?.value ?: ""

                                setLoading(true)

                                Axios.post<String>("${Config.apibase}/testplay/feedback", FeedbackUpdate(props.hash, newText), generateConfig<FeedbackUpdate, String>()).then({
                                    setText(newText)
                                    setTime(Clock.System.now().toString())
                                    setEditing(false)
                                    setLoading(false)
                                }) {
                                    setLoading(false)
                                }
                            }
                            attrs.disabled = loading
                            +"Save"
                        }
                    }
                    button(classes = "btn btn-info m-1") {
                        attrs.onClickFunction = {
                            setEditing(!editing)
                        }
                        attrs.disabled = loading
                        +(if (editing) "Cancel" else "Edit")
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
        attrs.bodyCallback = TimelineEntrySectionRenderer {
            if (editing) {
                textarea("10", classes = "form-control") {
                    ref = textareaRef
                    attrs.disabled = loading
                    +text
                }
            } else {
                textToContent(text)
            }
        }
        attrs.footerCallback = TimelineEntrySectionRenderer {
            small {
                TimeAgo.default {
                    key = time
                    attrs.date = time
                }
            }
        }
    }
}
