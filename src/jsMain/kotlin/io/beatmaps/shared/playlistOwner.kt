package io.beatmaps.shared

import external.TimeAgo
import external.routeLink
import io.beatmaps.api.UserDetail
import kotlinx.datetime.Instant
import react.Props
import react.fc

external interface PlaylistOwnerProps : Props {
    var owner: UserDetail?
    var time: Instant
}

val playlistOwner = fc<PlaylistOwnerProps> { props ->
    props.owner?.let { owner ->
        routeLink(owner.profileLink("playlists")) {
            +owner.name
        }
        +" - "
    }
    TimeAgo.default {
        attrs.date = props.time.toString()
    }
}
