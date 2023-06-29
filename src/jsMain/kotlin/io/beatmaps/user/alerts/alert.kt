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
import kotlinx.browser.document
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import org.w3c.dom.HTMLDivElement
import react.Props
import react.RBuilder
import react.RComponent
import react.State
import react.createRef
import react.dom.a
import react.dom.b
import react.dom.div
import react.dom.i
import react.dom.jsStyle
import react.dom.p
import react.dom.span
import react.setState

external interface AlertProps : Props {
    var alert: UserAlert?
    var read: Boolean?
    var hidden: Boolean?
    var markAlert: ((UserAlertStats) -> Unit)?
}

external interface AlertState : State {
    var height: String?
    var opacity: String?
    var margin: String?
}

fun updateAlertDisplay(stats: UserAlertStats) {
    document.getElementById("alert-count")?.apply {
        stats.unread.let { count ->
            setAttribute("data-count", count.toString())
            innerHTML = if (count < 10) count.toString() else "9+"
        }
    }
}

private fun markAlert(alert: UserAlert, read: Boolean, cb: (UserAlertStats) -> Unit) =
    Axios.post<UserAlertStats>(
        "${Config.apibase}/alerts/mark",
        AlertUpdate(alert.id, read),
        generateConfig<AlertUpdate, UserAlertStats>()
    ).then {
        updateAlertDisplay(it.data)

        cb(it.data)
    }.catch {
        // Bad request -> failed to mark
    }

class AlertElement : RComponent<AlertProps, AlertState>() {
    private val bodyRef = createRef<HTMLDivElement>()

    override fun componentWillReceiveProps(nextProps: AlertProps) {
        if (nextProps.alert == null) {
            setState {
                height = "auto"
                margin = "5px"
            }
        }
    }

    override fun componentDidUpdate(prevProps: AlertProps, prevState: AlertState, snapshot: Any) {
        if (props.hidden != prevProps.hidden || props.alert != prevProps.alert) {
            setState {
                if (props.hidden == true) {
                    height = "0px"
                    opacity = "0"
                    margin = "-1px 5px" // -1 pixel to account for the border
                } else if (bodyRef.current != null) {
                    val innerSize = bodyRef.current?.scrollHeight?.let { it + 49.5 } ?: 0

                    height = "${innerSize}px"
                    opacity = "1"
                    margin = null
                }
            }
        }
    }

    private fun respondCollaboration(accept: Boolean) = props.alert?.let { alert ->
        markAlert(alert, true) {
            props.markAlert?.invoke(UserAlertStats(it.unread, it.read - 1, it.byType.let { map ->
                map.toMutableMap().apply {
                    this[EAlertType.Collaboration]?.let { count ->
                        this[EAlertType.Collaboration] = count - 1
                    }

                    if (this[EAlertType.Collaboration] == 0) remove(EAlertType.Collaboration)
                }
            }))

            if (alert.collaborationId != null) {
                Axios.post<String>(
                    "${Config.apibase}/collaborations/response",
                    CollaborationResponseData(alert.collaborationId, accept),
                    generateConfig<CollaborationResponseData, String>()
                )
            }
        }
    }

    override fun RBuilder.render() {
        props.alert?.let { alert ->
            coloredCard {
                attrs.color = alert.type.color
                attrs.icon = alert.type.icon

                attrs.extra = { d ->
                    d.jsStyle {
                        height = state.height
                        opacity = state.opacity
                        margin = state.margin
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
                    height = "144px"
                }
            }
        }
    }
}

fun RBuilder.alert(handler: AlertProps.() -> Unit) =
    child(AlertElement::class) {
        this.attrs(handler)
    }
