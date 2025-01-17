package io.beatmaps.maps.testplay

import external.Axios
import external.TimeAgo
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.FeedbackUpdate
import io.beatmaps.util.textToContent
import kotlinx.datetime.Clock
import org.w3c.dom.HTMLTextAreaElement
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.small
import react.dom.html.ReactHTML.textarea
import react.fc
import react.useRef
import react.useState
import web.cssom.ClassName

external interface FeedbackProps : Props {
    var hash: String
    var name: String
    var feedback: String
    var time: String
    var isOwner: Boolean
}

val feedback = fc<FeedbackProps>("feedback") { props ->
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
                div {
                    attrs.className = ClassName("float-end")
                    if (editing) {
                        button {
                            attrs.className = ClassName("btn btn-success m-1")
                            attrs.onClick = {
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
                    button {
                        attrs.className = ClassName("btn btn-info m-1")
                        attrs.onClick = {
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
                textarea {
                    ref = textareaRef
                    attrs.rows = 10
                    attrs.className = ClassName("form-control")
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
