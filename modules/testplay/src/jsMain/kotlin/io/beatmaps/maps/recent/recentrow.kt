package io.beatmaps.maps.recent

import external.Axios
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.FeedbackUpdate
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapVersion
import io.beatmaps.util.fcmemo
import io.beatmaps.util.textToContent
import kotlinx.datetime.internal.JSJoda.Instant
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.textarea
import react.dom.html.ReactHTML.tr
import react.useRef
import react.useState
import web.cssom.ClassName
import web.html.HTMLTextAreaElement

external interface RecentTestplayRowProps : Props {
    var map: MapDetail
    var version: MapVersion
    var feedback: String?
    var time: String
}

val recentTestplayRow = fcmemo<RecentTestplayRowProps>("RecentTestplayRow") { props ->
    val textareaRef = useRef<HTMLTextAreaElement>()
    val (editing, setEditing) = useState(props.feedback == null)
    val (loading, setLoading) = useState(false)
    val (text, setText) = useState(props.feedback ?: "")
    val (time, setTime) = useState(props.time)

    beatmapTableRow {
        key = props.map.id
        map = props.map
        version = props.version
    }

    tr {
        td {
            colSpan = 4
            div {
                if (editing) {
                    textarea {
                        ref = textareaRef
                        rows = 10
                        className = ClassName("form-control")
                        disabled = loading == true
                        +text
                    }
                } else {
                    textToContent(text)
                }
            }
            div {
                className = ClassName("text-end mt-3")
                if (editing) {
                    button {
                        className = ClassName("btn btn-success m-1")
                        onClick = {
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
                        disabled = loading
                        +"Save"
                    }
                }
                button {
                    className = ClassName("btn btn-info m-1")
                    onClick = {
                        setEditing(!editing)
                    }
                    disabled = loading
                    +(if (editing) "Cancel" else "Edit")
                }
            }
        }
    }
}
