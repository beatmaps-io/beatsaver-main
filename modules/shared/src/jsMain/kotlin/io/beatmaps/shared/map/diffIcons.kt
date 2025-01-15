package io.beatmaps.shared.map

import io.beatmaps.api.MapDifficulty
import io.beatmaps.maps.diffImg
import io.beatmaps.util.fcmemo
import react.Props
import react.dom.span

external interface DiffIconsProps : Props {
    var diffs: List<MapDifficulty>?
}

val diffIcons = fcmemo<DiffIconsProps>("diffIcons") { props ->
    props.diffs?.forEach { d ->
        span("badge rounded-pill badge-${d.difficulty.color}") {
            diffImg {
                attrs.diff = d
            }
            +d.difficulty.human()
        }
    }
}
