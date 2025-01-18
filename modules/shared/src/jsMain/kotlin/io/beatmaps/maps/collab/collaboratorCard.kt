package io.beatmaps.maps.collab

import io.beatmaps.api.UserDetail
import io.beatmaps.util.fcmemo
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.span
import web.cssom.ClassName

external interface CollaboratorCardProps : Props {
    var user: UserDetail
    var callback: (() -> Unit)?
    var accepted: Boolean?
}

val collaboratorCard = fcmemo<CollaboratorCardProps>("collaboratorCard") { props ->
    div {
        className = ClassName("collaborator" + if (props.accepted != false) " accepted" else "")
        img {
            alt = props.user.name
            src = props.user.avatar
        }
        span {
            +props.user.name
            props.accepted?.let { status ->
                span {
                    className = ClassName("status")
                    +(if (status) "Accepted" else "Pending")
                }
            }
        }
        a {
            className = ClassName("btn-close")
            onClick = {
                props.callback?.invoke()
            }
        }
    }
}
