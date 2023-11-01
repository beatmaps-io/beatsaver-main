package io.beatmaps.user

import external.routeLink
import io.beatmaps.api.UserDetail
import kotlinx.html.js.onClickFunction
import react.Props
import react.dom.a
import react.dom.i
import react.fc

external interface UserLinkProps : Props {
    var user: UserDetail
    var callback: (() -> Unit)?
}

val userLink = fc<UserLinkProps> { props ->
    a("#", classes = "me-1") {
        attrs.onClickFunction = { ev ->
            ev.preventDefault()
            props.callback?.invoke()
        }
        +props.user.name
    }
    routeLink(props.user.profileLink()) {
        i("fas fa-external-link-alt") {}
    }
}
