package io.beatmaps.user.alerts

import external.Axios
import external.ClassName
import external.TimeAgo
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.AlertUpdate
import io.beatmaps.api.CollaborationResponseData
import io.beatmaps.api.UserAlert
import io.beatmaps.api.UserAlertStats
import io.beatmaps.common.api.EAlertType
import io.beatmaps.shared.coloredCard
import io.beatmaps.util.fcmemo
import io.beatmaps.util.textToContent
import io.beatmaps.util.updateAlertDisplay
import js.objects.jso
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import react.useEffect
import react.useRef
import react.useState
import web.cssom.Auto.Companion.auto
import web.cssom.Height
import web.cssom.Margin
import web.cssom.Opacity
import web.cssom.number
import web.cssom.px
import web.html.HTMLDivElement

external interface AlertProps : Props {
    var alert: UserAlert?
    var read: Boolean?
    var hidden: Boolean
    var markAlert: ((Int, UserAlertStats) -> Unit)?
}

private fun markAlert(alert: UserAlert, read: Boolean, cb: (Int, UserAlertStats) -> Unit) =
    Axios.post<UserAlertStats>(
        "${Config.apibase}/alerts/mark",
        AlertUpdate(alert.id ?: throw IllegalArgumentException(), read),
        generateConfig<AlertUpdate, UserAlertStats>()
    ).then {
        updateAlertDisplay(it.data)

        cb(alert.hashCode(), it.data)
    }.catch {
        // Bad request -> failed to mark
    }

val alert = fcmemo<AlertProps>("alert") { props ->
    val (height, setHeight) = useState<Height?>(null)
    val (opacity, setOpacity) = useState<Opacity?>(null)
    val (margin, setMargin) = useState<Margin?>(null)

    val bodyRef = useRef<HTMLDivElement>()

    useEffect(props.alert, props.hidden) {
        if (props.alert == null) {
            setHeight(auto)
            setMargin(5.px)
        } else if (props.hidden) {
            setHeight(0.px)
            setOpacity(number(0.0))
            setMargin(Margin((-1).px, 5.px)) // -1 pixel to account for the border
        } else if (bodyRef.current != null) {
            val innerSize = bodyRef.current?.scrollHeight?.let { it + 49.5 } ?: 0

            setHeight(innerSize.px)
            setOpacity(number(1.0))
            setMargin(null)
        }
    }

    fun respondCollaboration(accept: Boolean) = props.alert?.let { alert ->
        Axios.post<String>(
            "${Config.apibase}/collaborations/response",
            CollaborationResponseData(alert.collaborationId ?: throw IllegalStateException(), accept),
            generateConfig<CollaborationResponseData, String>()
        ).then {
            Axios.get<UserAlertStats>(
                "${Config.apibase}/alerts/stats",
                generateConfig<String, UserAlertStats>()
            ).then {
                updateAlertDisplay(it.data)

                props.markAlert?.invoke(alert.hashCode(), it.data)
            }
        }
    }

    props.alert?.let { alert ->
        coloredCard {
            color = alert.type.color
            icon = alert.type.icon

            extra = { d ->
                d.style = jso {
                    this.height = height
                    this.opacity = opacity
                    this.margin = margin
                }
            }

            div {
                className = ClassName("card-header d-flex")
                span {
                    b {
                        +alert.head
                    }
                    +" - "
                    TimeAgo.default {
                        date = alert.time.toString()
                    }
                }
                if (props.alert?.id != null) {
                    props.markAlert?.let { ma ->
                        div {
                            className = ClassName("link-buttons")
                            a {
                                href = "#"
                                title = if (props.read != true) "Mark as read" else "Mark as unread"
                                onClick = { ev ->
                                    ev.preventDefault()
                                    markAlert(alert, props.read != true, ma)
                                }

                                i {
                                    className = ClassName("fas text-info fa-eye" + if (props.read != true) "-slash" else "")
                                }
                            }
                        }
                    }
                }
            }
            div {
                ref = bodyRef
                className = ClassName("card-body text-break")
                p {
                    textToContent(alert.body)
                }

                if (alert.type == EAlertType.Collaboration) {
                    div {
                        className = ClassName("alert-buttons")
                        a {
                            +"Accept"
                            className = ClassName("btn btn-success")
                            onClick = { respondCollaboration(true) }
                        }
                        a {
                            +"Reject"
                            className = ClassName("btn btn-danger")
                            onClick = { respondCollaboration(false) }
                        }
                    }
                }
            }
        }
    } ?: run {
        div {
            style = jso {
                this.height = 144.px
            }
        }
    }
}
