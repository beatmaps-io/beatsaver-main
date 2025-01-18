package io.beatmaps.shared

import external.TimeAgo
import external.routeLink
import io.beatmaps.api.MapDetail
import io.beatmaps.api.UserDetail
import io.beatmaps.user.ProfileTab
import io.beatmaps.util.fcmemo
import kotlinx.datetime.Instant
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import web.cssom.ClassName

external interface ReviewerProps : Props {
    var reviewer: UserDetail?
    var map: MapDetail?
    var time: Instant
}

val reviewer = fcmemo<ReviewerProps>("reviewer") { props ->
    div {
        className = ClassName("owner")
        props.reviewer?.let { owner ->
            img {
                alt = owner.name
                src = owner.avatar
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
            date = props.time.toString()
        }
    }
}
