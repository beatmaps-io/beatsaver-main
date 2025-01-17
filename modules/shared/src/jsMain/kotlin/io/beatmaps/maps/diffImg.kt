package io.beatmaps.maps

import io.beatmaps.api.MapDifficulty
import io.beatmaps.util.fcmemo
import react.Props
import react.dom.html.ReactHTML.img
import web.cssom.ClassName

external interface DiffImgProps : Props {
    var diff: MapDifficulty
}

val diffImg = fcmemo<DiffImgProps>("diffImg") { props ->
    val humanText = props.diff.characteristic.human()

    img {
        attrs.alt = humanText
        attrs.src = "/static/icons/${humanText.lowercase()}.svg"
        attrs.className = ClassName("mode")
        attrs.title = props.diff.difficulty.human() + " " + props.diff.characteristic.human()
        attrs.width = 16.0
        attrs.height = 16.0
    }
}
