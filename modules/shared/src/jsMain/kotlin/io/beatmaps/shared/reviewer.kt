package io.beatmaps.shared

import external.TimeAgo
import external.routeLink
import io.beatmaps.api.MapDetail
import io.beatmaps.api.UserDetail
import io.beatmaps.user.ProfileTab
import kotlinx.datetime.Instant
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.fc
import web.cssom.ClassName

external interface ReviewerProps : Props {
    var reviewer: UserDetail?
    var map: MapDetail?
    var time: Instant
}

val reviewer = fc<ReviewerProps>("reviewer") { props ->
    div {
        attrs.className = ClassName("owner")
        props.reviewer?.let { owner ->
            img {
                attrs.alt = owner.name
                attrs.src = owner.avatar
            }
            routeLink(owner.profileLink(ProfileTab.REVIEWS)) {
                +owner.name
            }
        }
        props.map?.let { map ->
            +" on "
            routeLink(map.link()) {
                +map.name
            }
        }
        +" - "
        TimeAgo.default {
            attrs.date = props.time.toString()
        }
    }
}
