package io.beatmaps.maps.testplay

import external.Dropzone
import external.ReCAPTCHA
import external.TimeAgo
import external.recaptcha
import io.beatmaps.History
import io.beatmaps.api.MapDetail
import io.beatmaps.common.api.EMapState
import io.beatmaps.index.ModalComponent
import io.beatmaps.upload.simple
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.w3c.dom.HTMLElement
import react.Props
import react.RBuilder
import react.RComponent
import react.RefObject
import react.State
import react.createRef
import react.dom.article
import react.dom.br
import react.dom.div
import react.dom.i
import react.dom.small
import react.dom.strong
import react.setState

enum class EventType {
    Feedback, Play, Version
}
data class Event(val type: EventType, val state: EMapState?, val title: String, val body: String, val time: Instant, val hash: String, val userId: Int? = null, val secondaryTime: Instant? = null)

external interface TimelineProps : Props {
    var mapInfo: MapDetail
    var isOwner: Boolean
    var loggedInId: Int?
    var reloadMap: () -> Unit
    var history: History
    var modal: RefObject<ModalComponent>
}

external interface TimelineState : State {
    var errors: List<String>
    var loading: Boolean
}

class Timeline : RComponent<TimelineProps, TimelineState>() {
    private val captchaRef = createRef<ReCAPTCHA>()
    private val progressBarInnerRef = createRef<HTMLElement>()

    override fun componentWillMount() {
        setState {
            errors = listOf()
            loading = false
        }
    }

    override fun RBuilder.render() {
        val versionTimes = props.mapInfo.versions.associate { it.hash to it.createdAt }
        val events = props.mapInfo.versions.flatMap { v ->
            v.testplays?.let {
                it.flatMap { t ->
                    listOf(
                        Event(EventType.Play, null, t.user.name, " played the map", t.createdAt, v.hash),
                        t.feedbackAt?.let { at -> Event(EventType.Feedback, null, t.user.name, t.feedback ?: "", at, v.hash, t.user.id) }
                    )
                }
            }.let { it ?: listOf() } + listOf(
                Event(EventType.Version, v.state, "New version uploaded", v.feedback ?: "", v.createdAt, v.hash, secondaryTime = v.scheduledAt)
            )
        }.filterNotNull().sortedWith(compareByDescending<Event> { versionTimes[it.hash] }.thenByDescending { it.time })

        div("timeline") {
            // Watch out, this must be at the top
            div("line text-muted") {}

            val latestVersion = props.mapInfo.latestVersion()
            val givenFeedback = latestVersion?.testplays?.any { it.user.id == props.loggedInId && it.feedbackAt != null } == true || props.loggedInId == null
            if (props.isOwner) {
                article("card") {
                    div("card-header icon bg-success") {
                        i("fas fa-plus") {}
                    }
                    div("card-body") {
                        val now = Clock.System.now()
                        val recentVersions = props.mapInfo.versions.map { now.minus(it.createdAt).inWholeHours }.filter { it < 12 }

                        val hoursUntilNext = if (recentVersions.size > 1) {
                            12 - (recentVersions.maxOrNull() ?: 12)
                        } else {
                            0
                        }

                        val extraText = listOf("${recentVersions.size} / 2 uploads used in the last 12 hours").let {
                            if (hoursUntilNext > 0) {
                                it + "$hoursUntilNext hours until next upload"
                            } else {
                                it
                            }
                        }

                        Dropzone.default {
                            simple(
                                props.history, state.loading, state.errors.isNotEmpty(), progressBarInnerRef,
                                "Drag and drop some files here, or click to upload a new version", captchaRef,
                                {
                                    setState {
                                        loading = true
                                    }
                                    it.append("mapId", props.mapInfo.intId().toString())
                                },
                                {
                                    setState {
                                        errors = it
                                        loading = false
                                    }
                                },
                                extraInfo = extraText
                            ) {
                                setState {
                                    errors = listOf()
                                    loading = false
                                }
                                props.reloadMap()
                            }
                        }
                        if (!state.loading) {
                            state.errors.forEach {
                                div("invalid-feedback") {
                                    +it
                                }
                            }
                        }
                    }
                }
            } else if (!givenFeedback && latestVersion != null) {
                newFeedback {
                    hash = latestVersion.hash
                    captcha = captchaRef
                }
            }

            var first = true
            events.forEach {
                when (it.type) {
                    EventType.Version -> {
                        val version = props.mapInfo.versions.find { v -> v.hash == it.hash }
                        version {
                            hash = it.hash
                            downloadUrl = version?.downloadURL
                            diffs = version?.diffs
                            isOwner = props.isOwner
                            feedback = it.body
                            firstVersion = first
                            time = it.time.toString()
                            state = it.state
                            scheduledAt = it.secondaryTime
                            reloadMap = props.reloadMap
                            mapId = props.mapInfo.intId()
                            modal = props.modal
                            allowPublish = props.mapInfo.name.isNotEmpty()
                        }
                        first = false
                    }
                    EventType.Feedback ->
                        feedback {
                            hash = it.hash
                            isOwner = it.userId != null && it.userId == props.loggedInId
                            feedback = it.body
                            name = it.title
                            time = it.time.toString()
                        }
                    EventType.Play -> {
                        article("card card-outline") {
                            div("card-header icon bg-warning") {
                                i("fas fa-vial") {}
                            }
                            div("card-body") {
                                strong {
                                    +it.title
                                }
                                +it.body
                            }
                            small {
                                TimeAgo.default {
                                    attrs.date = it.time.toString()
                                }
                                br {}
                                +it.hash
                            }
                        }
                    }
                }
            }

            recaptcha(captchaRef)
        }
    }
}

fun RBuilder.timeline(handler: TimelineProps.() -> Unit) =
    child(Timeline::class) {
        this.attrs(handler)
    }
