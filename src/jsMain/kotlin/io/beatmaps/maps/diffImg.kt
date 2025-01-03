package io.beatmaps.maps

import io.beatmaps.api.MapDifficulty
import kotlinx.html.title
import react.Props
import react.dom.img
import react.fc

external interface DiffImgProps : Props {
    var diff: MapDifficulty
}

val diffImg = fc<DiffImgProps>("diffImg") { props ->
    val humanText = props.diff.characteristic.human()

    img(humanText, "/static/icons/${humanText.lowercase()}.svg", classes = "mode") {
        attrs.title = props.diff.difficulty.human() + " " + props.diff.characteristic.human()
        attrs.width = "16"
        attrs.height = "16"
    }
}
