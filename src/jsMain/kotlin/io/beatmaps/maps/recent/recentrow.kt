package io.beatmaps.maps.recent

import external.Axios
import external.generateConfig
import io.beatmaps.api.FeedbackUpdate
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapVersion
import io.beatmaps.common.Config
import io.beatmaps.index.ModalComponent
import io.beatmaps.index.beatmapTableRow
import io.beatmaps.util.textToContent
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
import react.dom.button
import react.dom.div
import react.dom.td
import react.dom.textarea
import react.dom.tr
import react.key
import react.setState

external interface RecentTestplayRowProps : RProps {
    var map: MapDetail
    var version: MapVersion
    var feedback: String?
    var time: String
    var modal: RReadableRef<ModalComponent>
}

data class RecentTestplayRowState(var editing: Boolean = false, var loading: Boolean = false, var text: String = "", var time: String = "") : RState

class RecentTestplayRow : RComponent<RecentTestplayRowProps, RecentTestplayRowState>() {
    private val textareaRef = createRef<TEXTAREA>()

    init {
        state = RecentTestplayRowState()
    }

    override fun componentWillMount() {
        setState {
            editing = props.feedback == null
            text = props.feedback ?: ""
            time = props.time
        }
    }

    override fun RBuilder.render() {
        beatmapTableRow {
            key = props.map.id.toString()
            map = props.map
            version = props.version
            modal = props.modal
        }

        tr {
            td {
                attrs.colSpan = "4"
                div {
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
                div("text-end mt-3") {
                    if (state.editing) {
                        button(classes = "btn btn-success m-1") {
                            attrs.onClickFunction = {
                                val newText = textareaRef.current?.asDynamic().value as String

                                setState {
                                    loading = true
                                }

                                Axios.post<String>("${Config.apibase}/testplay/feedback", FeedbackUpdate(props.version.hash, newText), generateConfig<FeedbackUpdate, String>()).then({
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
        }
    }
}

fun RBuilder.recentTestplayRow(handler: RecentTestplayRowProps.() -> Unit): ReactElement {
    return child(RecentTestplayRow::class) {
        this.attrs(handler)
    }
}
