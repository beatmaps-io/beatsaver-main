package io.beatmaps.shared

import external.routeLink
import io.beatmaps.api.UserDetail
import io.beatmaps.user.userTitles
import io.beatmaps.util.fcmemo
import react.Props
import react.dom.div
import react.dom.h4
import react.dom.img
import react.dom.p

external interface UserCardProps : Props {
    var user: UserDetail
}

var userCard = fcmemo<UserCardProps>("userCard") { props ->
    val id = props.user.id
    val avatar = props.user.avatar
    val username = props.user.name
    val titles = userTitles(props.user)

    div("d-flex align-items-center my-2") {
        img("Profile Image", avatar, classes = "rounded-circle me-3") {
            attrs.width = "50"
            attrs.height = "50"
        }
        div("d-inline") {
            routeLink("/profile/$id") {
                h4("mb-1") {
                    +username
                }
            }
            p("text-muted mb-1") {
                +titles.joinToString(", ")
            }
        }
    }
}
