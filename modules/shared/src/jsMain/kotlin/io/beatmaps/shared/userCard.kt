package io.beatmaps.shared

import external.routeLink
import react.Props
import react.dom.div
import react.dom.h4
import react.dom.img
import react.dom.p
import react.fc

external interface UserCardProps : Props {
    var id: Int
    var avatar: String
    var username: String
    var titles: List<String>
}

var userCard = fc<UserCardProps>("userCard") {
    div("d-flex align-items-center my-2") {
        img("Profile Image", it.avatar, classes = "rounded-circle me-3") {
            attrs.width = "50"
            attrs.height = "50"
        }
        div("d-inline") {
            routeLink("/profile/${it.id}") {
                h4("mb-1") {
                    +it.username
                }
            }
            p("text-muted mb-1") {
                +it.titles.joinToString(", ")
            }
        }
    }
}
