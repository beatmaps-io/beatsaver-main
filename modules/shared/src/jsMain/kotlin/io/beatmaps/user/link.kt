package io.beatmaps.user

import external.routeLink
import io.beatmaps.api.UserDetail
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.i
import react.fc
import web.cssom.ClassName

external interface UserLinkProps : Props {
    var user: UserDetail
    var callback: (() -> Unit)?
}

val userLink = fc<UserLinkProps>("userLink") { props ->
    a {
        attrs.href = "#"
        attrs.className = ClassName("me-1")
        attrs.onClick = { ev ->
            ev.preventDefault()
            props.callback?.invoke()
        }
        +props.user.name
    }
    routeLink(props.user.profileLink()) {
        i {
            attrs.className = ClassName("fas fa-external-link-alt")
        }
    }
}
