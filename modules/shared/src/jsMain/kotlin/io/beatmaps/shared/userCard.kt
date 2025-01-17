package io.beatmaps.shared

import external.routeLink
import io.beatmaps.api.UserDetail
import io.beatmaps.user.userTitles
import io.beatmaps.util.fcmemo
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h4
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.p
import web.cssom.ClassName

external interface UserCardProps : Props {
    var user: UserDetail
}

var userCard = fcmemo<UserCardProps>("userCard") { props ->
    val id = props.user.id
    val avatar = props.user.avatar
    val username = props.user.name
    val titles = userTitles(props.user)

    div {
        attrs.className = ClassName("d-flex align-items-center my-2")
        img {
            attrs.alt = "Profile Image"
            attrs.src = avatar
            attrs.className = ClassName("rounded-circle me-3")
            attrs.width = 50.0
            attrs.height = 50.0
        }
        div {
            attrs.className = ClassName("d-inline")
            routeLink("/profile/$id") {
                h4 {
                    attrs.className = ClassName("mb-1")
                    +username
                }
            }
            p {
                attrs.className = ClassName("text-muted mb-1")
                +titles.joinToString(", ")
            }
        }
    }
}
