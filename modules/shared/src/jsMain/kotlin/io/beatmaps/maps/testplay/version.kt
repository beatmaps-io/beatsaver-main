package io.beatmaps.maps.testplay

import external.Axios
import external.Moment
import external.TimeAgo
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.FeedbackUpdate
import io.beatmaps.api.MapDifficulty
import io.beatmaps.api.StateUpdate
import io.beatmaps.common.api.EMapState
import io.beatmaps.shared.ModalButton
import io.beatmaps.shared.ModalData
import io.beatmaps.shared.form.errors
import io.beatmaps.shared.modalContext
import io.beatmaps.util.fcmemo
import io.beatmaps.util.textToContent
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.small
import react.dom.html.ReactHTML.strong
import react.use
import react.useRef
import react.useState
import web.cssom.ClassName
import web.html.HTMLTextAreaElement

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
    var allowPublish: Boolean?
    var alreadyPublished: Boolean?
}
private const val testplayEnabled = false

val version = fcmemo<VersionProps>("version") { props ->
    val (state, setState) = useState(props.state)
    val (loading, setLoading) = useState(false)
    val (loadingState, setLoadingState) = useState(false)
    val (text, setText) = useState(props.feedback)
    val (time, setTime) = useState(props.time)
    val (scheduledAt, setScheduledAt) = useState(props.scheduledAt)
    val scheduleAt = useRef<Instant>()
    val alert = useRef(true)
    val errors = useRef(emptyList<String>())

    val textareaRef = useRef<HTMLTextAreaElement>()

    val modal = use(modalContext)

    val mapState = { nextState: EMapState ->
        setLoadingState(true)

        Axios.post<ActionResponse>("${Config.apibase}/testplay/state", StateUpdate(props.hash, nextState, props.mapId, scheduleAt = scheduleAt.current, alert = alert.current), generateConfig<StateUpdate, ActionResponse>(validStatus = arrayOf(200, 400))).then({
            if (it.data.success) {
                if (nextState == EMapState.Published) {
                    if (scheduleAt.current == null) {
                        props.reloadMap()
                        null
                    } else {
                        EMapState.Scheduled
                    }
                } else {
                    nextState
                }?.let { newState ->
                    setState(newState)
                    setScheduledAt(scheduleAt.current)
                }
                true
            } else {
                errors.current = it.data.errors
                false
            }
        }, {
            false
        }).finally {
            setLoadingState(false)
        }
    }

    val shouldDisable = loading || loadingState

    timelineEntry {
        icon = "fa-upload"
        color = "danger"
        headerCallback = TimelineEntrySectionRenderer {
            if (props.isOwner) {
                div {
                    className = ClassName("float-end")
                    if (props.firstVersion && textareaRef.current != null) {
                        button {
                            className = ClassName("btn btn-success m-1")
                            onClick = {
                                val newText = textareaRef.current?.value ?: ""
                                setLoading(true)

                                Axios.post<String>("${Config.apibase}/testplay/version", FeedbackUpdate(props.hash, newText), generateConfig<FeedbackUpdate, String>()).then({
                                    setText(newText)
                                    setTime(Clock.System.now().toString())
                                    setLoading(false)
                                }) {
                                    setLoading(false)
                                }
                            }
                            disabled = shouldDisable
                            +"Save"
                        }
                    }

                    if (state == EMapState.Uploaded || state == EMapState.Feedback) {
                        if (props.allowPublish == true) {
                            button {
                                className = ClassName("btn btn-danger m-1")
                                onClick = {
                                    alert.current = props.alreadyPublished != true
                                    scheduleAt.current = null
                                    errors.current = emptyList()

                                    modal?.current?.showDialog?.invoke(
                                        ModalData(
                                            "Are you sure?",
                                            buttons = listOf(ModalButton("Publish", "primary") { mapState(EMapState.Published) }, ModalButton("Cancel")),
                                            large = true
                                        ) {
                                            publishModal {
                                                callbackScheduleAt = {
                                                    scheduleAt.current = it
                                                }
                                                notifyFollowersRef = alert
                                            }

                                            errors {
                                                this.errors = errors.current
                                            }
                                        }
                                    )
                                }
                                disabled = shouldDisable
                                +"Publish"
                            }
                        } else {
                            button {
                                className = ClassName("btn btn-danger m-1")
                                disabled = true
                                +"Set a name to publish"
                            }
                        }
                        if (testplayEnabled && props.firstVersion) {
                            button {
                                className = ClassName("btn btn-info m-1")
                                onClick = {
                                    mapState(EMapState.Testplay)
                                }
                                disabled = shouldDisable
                                +"Add to testplay queue"
                            }
                        }
                    } else if (state == EMapState.Testplay) {
                        button {
                            className = ClassName("btn btn-danger m-1")
                            onClick = {
                                mapState(EMapState.Uploaded)
                            }
                            disabled = shouldDisable
                            +"Remove from testplay queue"
                        }
                    } else if (state == EMapState.Scheduled) {
                        button {
                            className = ClassName("btn btn-info m-1")
                            disabled = true
                            val formatted = Moment(scheduledAt.toString()).format("YYYY-MM-DD HH:mm")
                            +"Scheduled for $formatted"
                        }
                        button {
                            className = ClassName("btn btn-danger m-1")
                            onClick = {
                                mapState(EMapState.Uploaded)
                            }
                            disabled = shouldDisable
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
        bodyCallback = TimelineEntrySectionRenderer {
            if (props.isOwner) {
                val anyErrors = props.diffs?.any {
                    (it.paritySummary.errors / it.notes.toFloat()) > 0.1
                } ?: false

                if (anyErrors) {
                    div {
                        className = ClassName("alert alert-danger")
                        i {
                            className = ClassName("fas fa-exclamation-circle float-start mt-1 fa-2x")
                        }
                        p {
                            className = ClassName("ms-5")
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
                div {
                    className = ClassName("row")
                    chunk.forEach {
                        div {
                            className = ClassName("col-lg-3")
                            val error = (it.paritySummary.errors / it.notes.toFloat()) > 0.1

                            div {
                                className = ClassName("alert alert-" + if (error) "danger" else "info")
                                strong {
                                    if (error) {
                                        i {
                                            className = ClassName("fas fa-exclamation-circle me-1")
                                        }
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
        footerCallback = TimelineEntrySectionRenderer {
            small {
                TimeAgo.default {
                    date = time
                }
            }
        }
    }
}
