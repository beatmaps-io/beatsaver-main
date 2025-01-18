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
import io.beatmaps.util.fcmemo
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import react.Props
import react.Suspense
import react.dom.html.ReactHTML.article
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.small
import react.dom.html.ReactHTML.strong
import react.use
import react.useRef
import react.useState
import web.cssom.ClassName
import web.html.HTMLElement

enum class EventType {
    Feedback, Play, Version
}
data class Event(val type: EventType, val state: EMapState?, val title: String, val body: String, val time: Instant, val hash: String, val userId: Int? = null, val secondaryTime: Instant? = null)

external interface TimelineProps : Props {
    var mapInfo: MapDetail
    var reloadMap: () -> Unit
    var history: History
}

val timeline = fcmemo<TimelineProps>("timeline") { props ->
    val (errors, setErrors) = useState(listOf<UploadValidationInfo>())
    val (loading, setLoading) = useState(false)

    val captchaRef = useRef<ICaptchaHandler>()
    val progressBarInnerRef = useRef<HTMLElement>()

    val userData = use(globalContext)
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

    div {
        className = ClassName("timeline")
        // Watch out, this must be at the top
        div {
            className = ClassName("line text-muted")
        }

        val latestVersion = props.mapInfo.latestVersion()
        val givenFeedback = latestVersion?.testplays?.any { it.user.id == loggedInId && it.feedbackAt != null } == true || loggedInId == null
        if (isOwnerLocal) {
            article {
                className = ClassName("card")
                div {
                    className = ClassName("card-header icon bg-success")
                    i {
                        className = ClassName("fas fa-plus")
                    }
                }
                div {
                    className = ClassName("card-body")
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
                            props.history, loading, progressBarInnerRef,
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
                            validationErrors = errors
                        }
                    }
                }

                captcha {
                    key = "captcha"
                    this.captchaRef = captchaRef
                    page = "timeline"
                }
            }
        } else if (!givenFeedback && latestVersion != null) {
            Suspense {
                testplayModule.newFeedback {
                    hash = latestVersion.hash
                }
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
                        isOwner = isOwnerLocal
                        feedback = it.body
                        firstVersion = first
                        time = it.time.toString()
                        state = it.state
                        scheduledAt = it.secondaryTime
                        reloadMap = props.reloadMap
                        mapId = props.mapInfo.intId()
                        allowPublish = props.mapInfo.name.isNotEmpty()
                        alreadyPublished = (props.mapInfo.lastPublishedAt != null)
                    }
                    first = false
                }
                EventType.Feedback ->
                    // In testplay module
                    /*feedback {
                        hash = it.hash
                        isOwner = it.userId != null && it.userId == loggedInId
                        feedback = it.body
                        name = it.title
                        time = it.time.toString()
                    }*/
                    Unit
                EventType.Play -> {
                    article {
                        className = ClassName("card card-outline")
                        div {
                            className = ClassName("card-header icon bg-warning")
                            i {
                                className = ClassName("fas fa-vial")
                            }
                        }
                        div {
                            className = ClassName("card-body")
                            strong {
                                +it.title
                            }
                            +it.body
                        }
                        small {
                            TimeAgo.default {
                                date = it.time.toString()
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
