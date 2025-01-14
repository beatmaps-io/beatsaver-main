package io.beatmaps.maps.testplay

import external.Dropzone
import external.TimeAgo
import io.beatmaps.History
import io.beatmaps.api.MapDetail
import io.beatmaps.api.UploadValidationInfo
import io.beatmaps.captcha.ICaptchaHandler
import io.beatmaps.captcha.captcha
import io.beatmaps.common.api.EMapState
import io.beatmaps.globalContext
import io.beatmaps.shared.form.errors
import io.beatmaps.upload.simple
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.w3c.dom.HTMLElement
import react.Props
import react.dom.article
import react.dom.br
import react.dom.div
import react.dom.i
import react.dom.small
import react.dom.strong
import react.fc
import react.useContext
import react.useRef
import react.useState

enum class EventType {
    Feedback, Play, Version
}
data class Event(val type: EventType, val state: EMapState?, val title: String, val body: String, val time: Instant, val hash: String, val userId: Int? = null, val secondaryTime: Instant? = null)

external interface TimelineProps : Props {
    var mapInfo: MapDetail
    var reloadMap: () -> Unit
    var history: History
}

val timeline = fc<TimelineProps>("timeline") { props ->
    val (errors, setErrors) = useState(listOf<UploadValidationInfo>())
    val (loading, setLoading) = useState(false)

    val captchaRef = useRef<ICaptchaHandler>()
    val progressBarInnerRef = useRef<HTMLElement>()

    val userData = useContext(globalContext)
    val loggedInId = userData?.userId
    val isOwnerLocal = loggedInId == props.mapInfo.uploader.id

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
        val givenFeedback = latestVersion?.testplays?.any { it.user.id == loggedInId && it.feedbackAt != null } == true || loggedInId == null
        if (isOwnerLocal) {
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
                            props.history, loading, errors.isNotEmpty(), progressBarInnerRef,
                            "Drag and drop some files here, or click to upload a new version", captchaRef,
                            {
                                setLoading(true)
                                it.append("mapId", props.mapInfo.intId().toString())
                            },
                            {
                                setErrors(it)
                                setLoading(false)
                            },
                            extraInfo = extraText
                        ) {
                            setErrors(listOf())
                            setLoading(false)
                            props.reloadMap()
                        }
                    }
                    if (!loading) {
                        errors {
                            attrs.validationErrors = errors
                        }
                    }
                }

                captcha {
                    attrs.captchaRef = captchaRef
                    attrs.page = "timeline"
                }
            }
        } else if (!givenFeedback && latestVersion != null) {
            // In testplay module
            testplayModule.newFeedback {
                attrs.hash = latestVersion.hash
            }
        }

        var first = true
        events.forEach {
            when (it.type) {
                EventType.Version -> {
                    val version = props.mapInfo.versions.find { v -> v.hash == it.hash }
                    version {
                        attrs.hash = it.hash
                        attrs.downloadUrl = version?.downloadURL
                        attrs.diffs = version?.diffs
                        attrs.isOwner = isOwnerLocal
                        attrs.feedback = it.body
                        attrs.firstVersion = first
                        attrs.time = it.time.toString()
                        attrs.state = it.state
                        attrs.scheduledAt = it.secondaryTime
                        attrs.reloadMap = props.reloadMap
                        attrs.mapId = props.mapInfo.intId()
                        attrs.allowPublish = props.mapInfo.name.isNotEmpty()
                        attrs.alreadyPublished = (props.mapInfo.lastPublishedAt != null)
                    }
                    first = false
                }
                EventType.Feedback ->
                    // In testplay module
                    /*feedback {
                        attrs.hash = it.hash
                        attrs.isOwner = it.userId != null && it.userId == loggedInId
                        attrs.feedback = it.body
                        attrs.name = it.title
                        attrs.time = it.time.toString()
                    }*/
                    Unit
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
    }
}
