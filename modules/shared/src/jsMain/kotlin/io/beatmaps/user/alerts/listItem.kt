package io.beatmaps.user.alerts

import io.beatmaps.util.fcmemo
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.span
import web.cssom.ClassName

external interface AlertListItemProps : Props {
    var active: Boolean?
    var icon: String?
    var text: String?
    var count: Int?
    var action: (() -> Unit)?
}

val alertsListItem = fcmemo<AlertListItemProps>("alertsListItem") {
    a {
        href = "#"
        className = ClassName("list-group-item list-group-item-action d-flex justify-content-between align-items-center" + if (it.active == true) " active" else "")
        onClick = { ev ->
            ev.preventDefault()
            it.action?.invoke()
        }
        span {
            it.icon?.let { i ->
                i {
                    className = ClassName("fas $i me-2")
                }
            }
            it.text?.let { t ->
                +t
            }
        }
        it.count?.let { c ->
            span {
                className = ClassName("badge rounded-pill " + if (it.active == true) "bg-light" else "bg-primary")
                +"$c"
            }
        }
    }
}
