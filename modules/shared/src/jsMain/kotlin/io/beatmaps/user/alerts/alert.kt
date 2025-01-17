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
import org.w3c.dom.HTMLDivElement
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
            attrs.color = alert.type.color
            attrs.icon = alert.type.icon

            attrs.extra = { d ->
                d.style = jso {
                    this.height = height
                    this.opacity = opacity
                    this.margin = margin
                }
            }

            div {
                attrs.className = ClassName("card-header d-flex")
                span {
                    b {
                        +alert.head
                    }
                    +" - "
                    TimeAgo.default {
                        attrs.date = alert.time.toString()
                    }
                }
                if (props.alert?.id != null) {
                    props.markAlert?.let { ma ->
                        div {
                            attrs.className = ClassName("link-buttons")
                            a {
                                attrs.href = "#"
                                attrs.title = if (props.read != true) "Mark as read" else "Mark as unread"
                                attrs.onClick = { ev ->
                                    ev.preventDefault()
                                    markAlert(alert, props.read != true, ma)
                                }

                                i {
                                    attrs.className = ClassName("fas text-info fa-eye" + if (props.read != true) "-slash" else "")
                                }
                            }
                        }
                    }
                }
            }
            div {
                ref = bodyRef
                attrs.className = ClassName("card-body")
                p {
                    textToContent(alert.body)
                }

                if (alert.type == EAlertType.Collaboration) {
                    div {
                        attrs.className = ClassName("alert-buttons")
                        a {
                            +"Accept"
                            attrs.className = ClassName("btn btn-success")
                            attrs.onClick = { respondCollaboration(true) }
                        }
                        a {
                            +"Reject"
                            attrs.className = ClassName("btn btn-danger")
                            attrs.onClick = { respondCollaboration(false) }
                        }
                    }
                }
            }
        }
    } ?: run {
        div {
            attrs.style = jso {
                this.height = 144.px
            }
        }
    }
}
