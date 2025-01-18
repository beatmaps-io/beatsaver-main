package io.beatmaps.user

import external.routeLink
import io.beatmaps.api.UserDetail
import io.beatmaps.util.fcmemo
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.i
import web.cssom.ClassName

external interface UserLinkProps : Props {
    var user: UserDetail
    var callback: (() -> Unit)?
}

val userLink = fcmemo<UserLinkProps>("userLink") { props ->
    a {
        href = "#"
        className = ClassName("me-1")
        onClick = { ev ->
            ev.preventDefault()
            props.callback?.invoke()
        }
        +props.user.name
    }
    routeLink(props.user.profileLink()) {
        i {
            className = ClassName("fas fa-external-link-alt")
        }
    }
}
