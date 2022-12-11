package io.beatmaps.maps.testplay

import external.Axios
import external.Moment
import external.TimeAgo
import external.generateConfig
import external.reactFor
import io.beatmaps.Config
import io.beatmaps.api.FeedbackUpdate
import io.beatmaps.api.MapDifficulty
import io.beatmaps.api.StateUpdate
import io.beatmaps.common.api.EMapState
import io.beatmaps.index.ModalButton
import io.beatmaps.index.ModalComponent
import io.beatmaps.index.ModalData
import io.beatmaps.util.textToContent
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.html.InputType
import kotlinx.html.TEXTAREA
import kotlinx.html.id
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLInputElement
import react.Props
import react.RBuilder
import react.RComponent
import react.RefObject
import react.State
import react.createRef
import react.dom.a
import react.dom.article
import react.dom.br
import react.dom.button
import react.dom.defaultValue
import react.dom.div
import react.dom.h3
import react.dom.i
import react.dom.input
import react.dom.label
import react.dom.p
import react.dom.small
import react.dom.strong
import react.fc
import react.setState
import react.useState

external interface VersionProps : Props {
    var mapId: Int
    var hash: String
    var downloadUrl: String?
    var diffs: List<MapDifficulty>?
    var firstVersion: Boolean
    var feedback: String
    var time: String
    var isOwner: Boolean
    var state: EMapState?
    var scheduledAt: Instant?
    var reloadMap: () -> Unit
    var modal: RefObject<ModalComponent>
    var allowPublish: Boolean?
}

external interface VersionState : State {
    var state: EMapState?
    var loading: Boolean?
    var loadingState: Boolean?
    var text: String?
    var time: String?
    var scheduledAt: Instant?

    var scheduleAt: Instant?
}
private const val testplayEnabled = false

class VersionComponent : RComponent<VersionProps, VersionState>() {
    private val textareaRef = createRef<TEXTAREA>()

    override fun componentWillMount() {
        setState {
            state = props.state
            text = props.feedback
            time = props.time
            scheduledAt = props.scheduledAt
        }
    }

    private fun mapState(nextState: EMapState) {
        setState {
            loadingState = true
        }

        Axios.post<String>("${Config.apibase}/testplay/state", StateUpdate(props.hash, nextState, props.mapId, scheduleAt = state.scheduleAt), generateConfig<StateUpdate, String>()).then({
            if (nextState == EMapState.Published) {
                if (state.scheduleAt == null) {
                    props.reloadMap()
                    null
                } else {
                    EMapState.Scheduled
                }
            } else {
                nextState
            }?.let {
                setState {
                    state = it
                    loadingState = false
                    scheduledAt = scheduleAt
                }
            }
        }) {
            setState {
                loadingState = false
            }
        }
    }

    val publishModal = fc<Props> {
        val (publishType, setPublishType) = useState(false)

        p {
            +"This will make your map visible to everyone"
        }
        p {
            +"You should only publish maps that are completed, if you just want to test your map check out the guides here:"
            br {}
            a("https://bsmg.wiki/mapping/#playtesting") {
                +"https://bsmg.wiki/mapping/#playtesting"
            }
        }
        p {
            +"You should also consider getting your map playtested by other mappers for feedback first"
        }
        p {
            +"Uploading new versions later will cause leaderboards for your map to be reset"
        }
        div("mb-3") {
            div("form-check check-border") {
                label("form-check-label") {
                    input(InputType.radio, classes = "form-check-input") {
                        attrs.name = "publishType"
                        attrs.id = "publishTypeNow"
                        attrs.value = "now"
                        attrs.defaultChecked = true
                        attrs.onChangeFunction = {
                            setState {
                                scheduleAt = null
                            }
                            setPublishType(false)
                        }
                    }
                    +"Release immediately"
                }
            }

            div("form-check check-border") {
                label("form-check-label") {
                    attrs.reactFor = "publishTypeSchedule"
                    input(InputType.radio, classes = "form-check-input") {
                        attrs.name = "publishType"
                        attrs.id = "publishTypeSchedule"
                        attrs.value = "schedule"
                        attrs.onChangeFunction = {
                            setPublishType(true)
                        }
                    }
                    +"Schedule release"

                    if (publishType) {
                        input(InputType.dateTimeLocal, classes = "form-control m-2") {
                            attrs.id = "scheduleAt"
                            val nowStr = Moment().format("YYYY-MM-DDTHH:mm")
                            attrs.defaultValue = nowStr
                            attrs.min = nowStr
                            attrs.onChangeFunction = {
                                setState {
                                    val textVal = (it.target as HTMLInputElement).value
                                    scheduleAt = if (textVal.isEmpty()) null else Instant.parse(Moment(textVal).toISOString())
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun RBuilder.render() {
        val shouldDisable = state.loading == true || state.loadingState == true

        article("card border-danger") {
            div("card-header icon bg-danger") {
                i("fas fa-upload") {}
            }
            div("card-header") {
                if (props.isOwner) {
                    div("float-end") {
                        if (props.firstVersion && textareaRef.current != null) {
                            button(classes = "btn btn-success m-1") {
                                attrs.onClickFunction = {
                                    val newText = textareaRef.current?.asDynamic().value as String

                                    setState {
                                        loading = true
                                    }

                                    Axios.post<String>("${Config.apibase}/testplay/version", FeedbackUpdate(props.hash, newText), generateConfig<FeedbackUpdate, String>()).then({
                                        setState {
                                            text = newText
                                            time = Clock.System.now().toString()
                                            loading = false
                                        }
                                    }) {
                                        setState {
                                            loading = false
                                        }
                                    }
                                }
                                attrs.disabled = shouldDisable
                                +"Save"
                            }
                        }

                        if (state.state == EMapState.Uploaded || state.state == EMapState.Feedback) {
                            if (props.allowPublish == true) {
                                button(classes = "btn btn-danger m-1") {
                                    attrs.onClickFunction = {
                                        props.modal.current?.showDialog(
                                            ModalData(
                                                "Are you sure?",
                                                buttons = listOf(ModalButton("Publish", "primary") { mapState(EMapState.Published) }, ModalButton("Cancel")),
                                                large = true
                                            ) {
                                                setState {
                                                    scheduleAt = null
                                                }
                                                publishModal { }
                                            }
                                        )
                                    }
                                    attrs.disabled = shouldDisable
                                    +"Publish"
                                }
                            } else {
                                button(classes = "btn btn-danger m-1") {
                                    attrs.disabled = true
                                    +"Set a name to publish"
                                }
                            }
                            if (testplayEnabled && props.firstVersion) {
                                button(classes = "btn btn-info m-1") {
                                    attrs.onClickFunction = {
                                        mapState(EMapState.Testplay)
                                    }
                                    attrs.disabled = shouldDisable
                                    +"Add to testplay queue"
                                }
                            }
                        } else if (state.state == EMapState.Testplay) {
                            button(classes = "btn btn-danger m-1") {
                                attrs.onClickFunction = {
                                    mapState(EMapState.Uploaded)
                                }
                                attrs.disabled = shouldDisable
                                +"Remove from testplay queue"
                            }
                        } else if (state.state == EMapState.Scheduled) {
                            button(classes = "btn btn-info m-1") {
                                attrs.disabled = true
                                val formatted = Moment(state.scheduledAt.toString()).format("YYYY-MM-DD HH:mm")
                                +"Scheduled for $formatted"
                            }
                            button(classes = "btn btn-danger m-1") {
                                attrs.onClickFunction = {
                                    mapState(EMapState.Uploaded)
                                }
                                attrs.disabled = shouldDisable
                                +"Cancel"
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
                            i("fas fa-exclamation-circle float-start mt-1 fa-2x") {}
                            p("ms-5") {
                                textToContent(
                                    "Some of your difficulties have a high percentage of parity errors\n\n" +
                                        "You can read more about parity on the BSMG wiki:\nhttps://bsmg.wiki/mapping/basic-mapping.html#do-mapping-with-flow\n\n" +
                                        "To check these errors yourself visit the parity checker here:\nhttps://galaxymaster2.github.io/bs-parity?url=${props.downloadUrl}"
                                )
                            }
                        }
                    }
                }

                /*
                if (props.isOwner && props.firstVersion && (state.state != EMapState.Uploaded || testplayEnabled)) {
                    p {
                        +"Tell testplayers what kind of feedback you're looking for:"
                    }
                    textarea("10", classes = "form-control mb-4") {
                        ref = textareaRef
                        +(state.text ?: "")
                    }
                } else {
                    p {
                        state.text?.let { textToContent(it) }
                    }
                }
                 */

                props.diffs?.chunked(4) { chunk ->
                    div("row") {
                        chunk.forEach {
                            div("col-lg-3") {
                                val error = (it.paritySummary.errors / it.notes.toFloat()) > 0.1

                                div("alert alert-" + if (error) "danger" else "info") {
                                    strong {
                                        if (error) {
                                            i("fas fa-exclamation-circle me-1") {}
                                        }
                                        +"${it.characteristic.human()} - ${it.difficulty.human()}"
                                    }
                                    p {
                                        textToContent(
                                            "Notes: ${it.notes}\nBombs: ${it.bombs}\nObstacles: ${it.obstacles}\nEvents: ${it.events}\n" +
                                                "NPS: ${it.nps}\nParity: ${it.paritySummary.errors} (${it.paritySummary.warns})"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            div("card-footer") {
                small {
                    state.time?.let {
                        TimeAgo.default {
                            attrs.date = it
                        }
                    }
                }
            }
        }
    }
}

fun RBuilder.version(handler: VersionProps.() -> Unit) =
    child(VersionComponent::class) {
        this.attrs(handler)
    }
