package io.beatmaps.shared.map

import io.beatmaps.api.MapDifficulty
import io.beatmaps.maps.diffImg
import react.Props
import react.dom.span
import react.fc

external interface DiffIconsProps : Props {
    var diffs: List<MapDifficulty>?
}

val diffIcons = fc<DiffIconsProps>("diffIcons") { props ->
    props.diffs?.forEach { d ->
        span("badge rounded-pill badge-${d.difficulty.color}") {
            diffImg {
                attrs.diff = d
            }
            +d.difficulty.human()
        }
    }
}
