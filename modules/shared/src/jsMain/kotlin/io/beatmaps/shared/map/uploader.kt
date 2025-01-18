package io.beatmaps.shared.map

import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapVersion
import io.beatmaps.common.api.EMapState
import io.beatmaps.shared.itemUserInfo
import io.beatmaps.util.fcmemo
import react.Props

external interface UploaderProps : Props {
    var map: MapDetail
    var version: MapVersion?
    var info: Boolean?
}

val uploaderWithInfo = fcmemo<UploaderProps>("uploaderWithInfo") { props ->
    itemUserInfo {
        users = listOf(props.map.uploader) + (props.map.collaborators ?: emptyList())
        time = if (props.version?.state == EMapState.Published) props.map.uploaded else null

        if (props.info != false && props.map.declaredAi.markAsBot) botInfo { }
    }
}
