package io.beatmaps.maps

import io.beatmaps.api.UserDetail
import kotlinx.html.js.onClickFunction
import react.Props
import react.dom.a
import react.dom.div
import react.dom.img
import react.dom.span
import react.fc

external interface CollaboratorCardProps : Props {
    var user: UserDetail
    var callback: (() -> Unit)?
    var accepted: Boolean?
}

val collaboratorCard = fc<CollaboratorCardProps> { props ->
    div("collaborator" + if (props.accepted != false) " accepted" else "") {
        img(props.user.name, props.user.avatar) { }
        span {
            +props.user.name
            props.accepted?.let { status ->
                span("status") {
                    +(if (status) "Accepted" else "Pending")
                }
            }
        }
        a(classes = "btn-close") {
            attrs.onClickFunction = {
                props.callback?.invoke()
            }
        }
    }
}