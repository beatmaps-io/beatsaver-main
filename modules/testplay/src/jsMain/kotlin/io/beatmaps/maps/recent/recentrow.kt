package io.beatmaps.maps.recent

import external.Axios
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.FeedbackUpdate
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapVersion
import io.beatmaps.util.textToContent
import kotlinx.datetime.internal.JSJoda.Instant
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLTextAreaElement
import react.Props
import react.dom.button
import react.dom.div
import react.dom.td
import react.dom.textarea
import react.dom.tr
import react.fc
import react.useRef
import react.useState

external interface RecentTestplayRowProps : Props {
    var map: MapDetail
    var version: MapVersion
    var feedback: String?
    var time: String
}

val recentTestplayRow = fc<RecentTestplayRowProps>("RecentTestplayRow") { props ->
    val textareaRef = useRef<HTMLTextAreaElement>()
    val (editing, setEditing) = useState(props.feedback == null)
    val (loading, setLoading) = useState(false)
    val (text, setText) = useState(props.feedback ?: "")
    val (time, setTime) = useState(props.time)

    beatmapTableRow {
        attrs.key = props.map.id
        attrs.map = props.map
        attrs.version = props.version
    }

    tr {
        td {
            attrs.colSpan = "4"
            div {
                if (editing) {
                    textarea("10", classes = "form-control") {
                        ref = textareaRef
                        attrs.disabled = loading == true
                        +text
                    }
                } else {
                    textToContent(text)
                }
            }
            div("text-end mt-3") {
                if (editing) {
                    button(classes = "btn btn-success m-1") {
                        attrs.onClickFunction = {
                            val newText = textareaRef.current?.value ?: ""

                            setLoading(true)

                            Axios.post<String>("${Config.apibase}/testplay/feedback", FeedbackUpdate(props.version.hash, newText), generateConfig<FeedbackUpdate, String>()).then {
                                setText(newText)
                                setTime(Instant.now().toString())
                                setEditing(false)
                            }.finally {
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
    }
}
