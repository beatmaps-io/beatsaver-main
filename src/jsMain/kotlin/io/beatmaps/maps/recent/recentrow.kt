package io.beatmaps.maps.recent

import external.Axios
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.FeedbackUpdate
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapVersion
import io.beatmaps.index.ModalComponent
import io.beatmaps.index.beatmapTableRow
import io.beatmaps.util.textToContent
import kotlinx.datetime.internal.JSJoda.Instant
import kotlinx.html.TEXTAREA
import kotlinx.html.js.onClickFunction
import react.Props
import react.RBuilder
import react.RComponent
import react.RefObject
import react.State
import react.createRef
import react.dom.button
import react.dom.div
import react.dom.td
import react.dom.textarea
import react.dom.tr
import react.dom.value
import react.key
import react.setState

external interface RecentTestplayRowProps : Props {
    var map: MapDetail
    var version: MapVersion
    var feedback: String?
    var time: String
    var modal: RefObject<ModalComponent>
}

external interface RecentTestplayRowState : State {
    var editing: Boolean?
    var loading: Boolean?
    var text: String?
    var time: String?
}

class RecentTestplayRow : RComponent<RecentTestplayRowProps, RecentTestplayRowState>() {
    private val textareaRef = createRef<TEXTAREA>()

    override fun componentWillMount() {
        setState {
            editing = props.feedback == null
            text = props.feedback ?: ""
            time = props.time
        }
    }

    override fun RBuilder.render() {
        beatmapTableRow {
            key = props.map.id
            map = props.map
            version = props.version
            modal = props.modal
        }

        tr {
            td {
                attrs.colSpan = "4"
                div {
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
                div("text-end mt-3") {
                    if (state.editing == true) {
                        button(classes = "btn btn-success m-1") {
                            attrs.onClickFunction = {
                                val newText = textareaRef.current?.value ?: ""

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
        }
    }
}

fun RBuilder.recentTestplayRow(handler: RecentTestplayRowProps.() -> Unit) =
    child(RecentTestplayRow::class) {
        this.attrs(handler)
    }
