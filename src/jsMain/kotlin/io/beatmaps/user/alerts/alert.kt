package io.beatmaps.user.alerts

import external.Axios
import external.TimeAgo
import external.generateConfig
import io.beatmaps.api.AlertUpdate
import io.beatmaps.api.UserAlert
import io.beatmaps.api.UserAlertStats
import io.beatmaps.common.Config
import io.beatmaps.shared.coloredCard
import io.beatmaps.util.textToContent
import kotlinx.browser.window
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import org.w3c.dom.HTMLDivElement
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
import react.createRef
import react.dom.a
import react.dom.b
import react.dom.div
import react.dom.i
import react.dom.jsStyle
import react.dom.span
import react.setState

external interface AlertProps : RProps {
    var alert: UserAlert?
    var read: Boolean?
    var hidden: Boolean?
    var markAlert: ((UserAlertStats) -> Unit)?
}

external interface AlertState : RState {
    var height: String?
    var opacity: String?
}

fun updateAlertDisplay(stats: UserAlertStats) {
    window.document.getElementById("alert-count")?.apply {
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
            }
        }
    }

    override fun componentDidUpdate(prevProps: AlertProps, prevState: AlertState, snapshot: Any) {
        if (props.hidden != prevProps.hidden || props.alert != prevProps.alert) {
            setState {
                if (props.hidden == true) {
                    height = "0px"
                    opacity = "0"
                } else if (bodyRef.current != null) {
                    val innerSize = bodyRef.current?.scrollHeight?.let { it + 49.5 } ?: 0

                    height = "${innerSize}px"
                    opacity = "1"
                }
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
                                attrs.title = "Mark as read"
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
                    textToContent(alert.body)
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

fun RBuilder.alert(handler: AlertProps.() -> Unit): ReactElement {
    return child(AlertElement::class) {
        this.attrs(handler)
    }
}
