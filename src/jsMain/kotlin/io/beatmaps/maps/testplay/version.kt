package io.beatmaps.maps.testplay

import Axios
import AxiosRequestConfig
import external.TimeAgo
import generateConfig
import io.beatmaps.common.Config
import io.beatmaps.common.api.EMapState
import io.beatmaps.api.FeedbackUpdate
import io.beatmaps.api.MapDifficulty
import io.beatmaps.api.StateUpdate
import io.beatmaps.index.ModalButton
import io.beatmaps.index.ModalComponent
import io.beatmaps.index.ModalData
import io.beatmaps.maps.textToContent
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
import react.dom.*
import react.setState

external interface VersionProps : RProps {
    var mapId: Int
    var hash: String
    var diffs: List<MapDifficulty>?
    var firstVersion: Boolean
    var feedback: String
    var time: String
    var isOwner: Boolean
    var state: EMapState?
    var reloadMap: () -> Unit
    var modal: RReadableRef<ModalComponent>
}

data class VersionState(var state: EMapState? = null, var loading: Boolean = false, var loadingState: Boolean = false, var text: String = "", var time: String = "") : RState
private const val testplayEnabled = false

@JsExport
class VersionComponent : RComponent<VersionProps, VersionState>() {
    private val textareaRef = createRef<TEXTAREA>()

    init {
        state = VersionState()
    }

    override fun componentWillMount() {
        setState {
            state = props.state
            text = props.feedback
            time = props.time
        }
    }

    private fun mapState(nextState: EMapState) {
        setState {
            loadingState = true
        }

        Axios.post<String>("/api/testplay/state", StateUpdate(props.hash, nextState, props.mapId), generateConfig<StateUpdate, String>()).then({
            if (nextState == EMapState.Published) {
                props.reloadMap()
            }

            setState {
                state = nextState
                loadingState = false
            }
        }) {
            setState {
                loadingState = false
            }
        }
    }

    override fun RBuilder.render() {
        article("card border-danger") {
            div("card-header icon bg-danger") {
                i("fas fa-upload") {}
            }
            div("card-header") {
                if (props.isOwner) {
                    div("float-right") {
                        if (props.firstVersion && textareaRef.current != null) {
                            button(classes = "btn btn-success m-1") {
                                attrs.onClickFunction = {
                                    val newText = textareaRef.current?.asDynamic().value as String

                                    setState {
                                        loading = true
                                    }

                                    Axios.post<String>("/api/testplay/version", FeedbackUpdate(props.hash, newText), generateConfig<FeedbackUpdate, String>()).then({
                                        setState {
                                            text = newText
                                            time = Instant.now().toString()
                                            loading = false
                                        }
                                    }) {
                                        setState {
                                            loading = false
                                        }
                                    }
                                }
                                attrs.disabled = state.loading || state.loadingState
                                +"Save"
                            }
                        }

                        if (props.firstVersion && (state.state == EMapState.Uploaded || state.state == EMapState.Feedback)) {
                            button(classes = "btn btn-danger m-1") {
                                attrs.onClickFunction = {
                                    props.modal.current?.showDialog(ModalData(
                                        "Are you sure?",
                                        "This will make your map visible to everyone\n\nYou should only publish maps that are completed, " +
                                                "if you just want to test your map check out the guides here:\nhttps://bsmg.wiki/mapping/#playtesting\n\n" +
                                                //"You should also consider placing your map into the playtest queue for feedback first\n\n" +
                                                "You should also consider getting your map playtested by other mappers for feedback first\n\n" +
                                                "Uploading new versions later will cause leaderboards for your map to be reset",
                                        listOf(ModalButton("Publish", "primary") { mapState(EMapState.Published) }, ModalButton("Cancel")),
                                        true
                                    ))
                                }
                                attrs.disabled = state.loading || state.loadingState
                                +"Publish"
                            }
                            if (testplayEnabled) {
                                button(classes = "btn btn-info m-1") {
                                    attrs.onClickFunction = {
                                        mapState(EMapState.Testplay)
                                    }
                                    attrs.disabled = state.loading || state.loadingState
                                    +"Add to testplay queue"
                                }
                            }
                        } else if (state.state == EMapState.Testplay) {
                            button(classes = "btn btn-danger m-1") {
                                attrs.onClickFunction = {
                                    mapState(EMapState.Uploaded)
                                }
                                attrs.disabled = state.loading || state.loadingState
                                +"Remove from testplay queue"
                            }
                        }
                    }
                }
                h3 {
                    +"New version uploaded"
                }
                small {
                    +props.hash
                }
            }
            div("card-body") {
                if (props.isOwner) {
                    val anyErrors = props.diffs?.any {
                        (it.paritySummary.errors / it.notes.toFloat()) > 0.1
                    } ?: false

                    if (anyErrors) {
                        div("alert alert-danger") {
                            i("fas fa-exclamation-circle float-left mt-1 fa-2x") {}
                            p("ml-5") {
                                textToContent(
                                    "Some of your difficulties have a high percentage of parity errors\n\n" +
                                            "You can read more about parity on the BSMG wiki:\nhttps://bsmg.wiki/mapping/basic-mapping.html#do-mapping-with-flow\n\n" +
                                            "To check these errors yourself visit the paity checker here:\nhttps://galaxymaster2.github.io/bs-parity?url=${Config.cdnbase}/${props.hash}.zip"
                                )
                            }
                        }
                    }
                }

                if (props.isOwner && props.firstVersion && (state.state != EMapState.Uploaded || testplayEnabled)) {
                    p {
                        +"Tell testplayers what kind of feedback you're looking for:"
                    }
                    textarea("10", classes ="form-control mb-4") {
                        ref = textareaRef
                        +state.text
                    }
                } else {
                    p {
                        textToContent(state.text)
                    }
                }

                props.diffs?.chunked(4) { chunk ->
                    div("row") {
                        chunk.forEach {
                            div("col-lg-3") {
                                val error = (it.paritySummary.errors / it.notes.toFloat()) > 0.1

                                div("alert alert-" + if (error) "danger" else "info") {
                                    strong {
                                        if (error) {
                                            i("fas fa-exclamation-circle mr-1") {}
                                        }
                                        +"${it.characteristic.human()} - ${it.difficulty.human()}"
                                    }
                                    p {
                                        textToContent("Notes: ${it.notes}\nBombs: ${it.bombs}\nObstacles: ${it.obstacles}\nEvents: ${it.events}\n" +
                                                "NPS: ${it.nps}\nParity: ${it.paritySummary.errors} (${it.paritySummary.warns})")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            div("card-footer") {
                small {
                    TimeAgo.default {
                        attrs.date = state.time
                    }
                }
            }
        }
    }
}

fun RBuilder.version(handler: VersionProps.() -> Unit): ReactElement {
    return child(VersionComponent::class) {
        this.attrs(handler)
    }
}