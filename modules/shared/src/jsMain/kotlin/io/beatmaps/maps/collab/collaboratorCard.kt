package io.beatmaps.maps.collab

import io.beatmaps.api.UserDetail
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.span
import react.fc
import web.cssom.ClassName

external interface CollaboratorCardProps : Props {
    var user: UserDetail
    var callback: (() -> Unit)?
    var accepted: Boolean?
}

val collaboratorCard = fc<CollaboratorCardProps>("collaboratorCard") { props ->
    div {
        attrs.className = ClassName("collaborator" + if (props.accepted != false) " accepted" else "")
        img {
            attrs.alt = props.user.name
            attrs.src = props.user.avatar
        }
        span {
            +props.user.name
            props.accepted?.let { status ->
                span {
                    attrs.className = ClassName("status")
                    +(if (status) "Accepted" else "Pending")
                }
            }
        }
        a {
            attrs.className = ClassName("btn-close")
            attrs.onClick = {
                props.callback?.invoke()
            }
        }
    }
}
