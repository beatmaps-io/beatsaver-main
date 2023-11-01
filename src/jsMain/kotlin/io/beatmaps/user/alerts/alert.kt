package io.beatmaps.user.alerts

import external.Axios
import external.TimeAgo
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.AlertUpdate
import io.beatmaps.api.CollaborationResponseData
import io.beatmaps.api.UserAlert
import io.beatmaps.api.UserAlertStats
import io.beatmaps.common.api.EAlertType
import io.beatmaps.shared.coloredCard
import io.beatmaps.util.textToContent
import io.beatmaps.util.updateAlertDisplay
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import org.w3c.dom.HTMLDivElement
import react.Props
import react.createRef
import react.dom.a
import react.dom.b
import react.dom.div
import react.dom.i
import react.dom.jsStyle
import react.dom.p
import react.dom.span
import react.fc
import react.useEffect
import react.useState

external interface AlertProps : Props {
    var alert: UserAlert?
    var read: Boolean?
    var hidden: Boolean
    var markAlert: ((UserAlertStats) -> Unit)?
}

private fun markAlert(alert: UserAlert, read: Boolean, cb: (UserAlertStats) -> Unit) =
    Axios.post<UserAlertStats>(
        "${Config.apibase}/alerts/mark",
        AlertUpdate(alert.id ?: throw IllegalArgumentException(), read),
        generateConfig<AlertUpdate, UserAlertStats>()
    ).then {
        updateAlertDisplay(it.data)

        cb(it.data)
    }.catch {
        // Bad request -> failed to mark
    }

val alert = fc<AlertProps> { props ->
    val (height, setHeight) = useState<String?>(null)
    val (opacity, setOpacity) = useState<String?>(null)
    val (margin, setMargin) = useState<String?>(null)

    val bodyRef = createRef<HTMLDivElement>()

    useEffect(props.alert, props.hidden) {
        if (props.alert == null) {
            setHeight("auto")
            setMargin("5px")
        } else if (props.hidden) {
            setHeight("0px")
            setOpacity("0")
            setMargin("-1px 5px") // -1 pixel to account for the border
        } else if (bodyRef.current != null) {
            val innerSize = bodyRef.current?.scrollHeight?.let { it + 49.5 } ?: 0

            setHeight("${innerSize}px")
            setOpacity("1")
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

                props.markAlert?.invoke(it.data)
            }
        }
    }

    props.alert?.let { alert ->
        coloredCard {
            attrs.color = alert.type.color
            attrs.icon = alert.type.icon

            attrs.extra = { d ->
                d.jsStyle {
                    this.height = height
                    this.opacity = opacity
                    this.margin = margin
                }
            }

            div("card-header d-flex") {
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
                        div("ms-auto flex-shrink-0") {
                            a("#") {
                                attrs.title = if (props.read != true) "Mark as read" else "Mark as unread"
                                attrs.onClickFunction = { ev ->
                                    ev.preventDefault()
                                    markAlert(alert, props.read != true, ma)
                                }

                                i("fas text-info fa-eye" + if (props.read != true) "-slash" else "") { }
                            }
                        }
                    }
                }
            }
            div("card-body") {
                ref = bodyRef
                p {
                    textToContent(alert.body)
                }

                if (alert.type == EAlertType.Collaboration) {
                    div("alert-buttons") {
                        a(classes = "btn btn-success") {
                            +"Accept"
                            attrs.onClickFunction = { respondCollaboration(true) }
                        }
                        a(classes = "btn btn-danger") {
                            +"Reject"
                            attrs.onClickFunction = { respondCollaboration(false) }
                        }
                    }
                }
            }
        }
    } ?: run {
        div {
            attrs.jsStyle {
                this.height = "144px"
            }
        }
    }
}
