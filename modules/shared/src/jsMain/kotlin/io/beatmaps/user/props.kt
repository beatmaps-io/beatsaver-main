package io.beatmaps.user

import org.w3c.dom.HTMLDivElement
import react.Props

external interface FollowListProps : Props {
    var scrollParent: HTMLDivElement?
    var following: Int?
    var followedBy: Int?
}