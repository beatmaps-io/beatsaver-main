package io.beatmaps.user

import react.Props
import web.html.HTMLDivElement

external interface FollowListProps : Props {
    var scrollParent: HTMLDivElement?
    var following: Int?
    var followedBy: Int?
}
