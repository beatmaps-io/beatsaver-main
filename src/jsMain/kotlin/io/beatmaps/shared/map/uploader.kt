package io.beatmaps.shared.map

import external.TimeAgo
import external.routeLink
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapVersion
import io.beatmaps.common.api.EMapState
import react.Props
import react.fc

external interface UploaderProps : Props {
    var map: MapDetail
    var version: MapVersion?
}

val uploader = fc<UploaderProps> { props ->
    (listOf(props.map.uploader) + (props.map.collaborators ?: listOf())).let {
        it.forEachIndexed { idx, u ->
            routeLink(u.profileLink()) {
                +u.name
            }
            if (idx < it.lastIndex) +", "
        }
    }
}

val uploaderWithInfo = fc<UploaderProps> { props ->
    uploader {
        attrs.map = props.map
    }
    if (props.map.declaredAi.markAsBot) botInfo { }
    if (props.version?.state == EMapState.Published) {
        +" - "
        TimeAgo.default {
            attrs.date = props.map.uploaded.toString()
        }
    }
}
