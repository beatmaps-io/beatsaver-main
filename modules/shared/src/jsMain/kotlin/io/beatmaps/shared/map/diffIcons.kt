package io.beatmaps.shared.map

import io.beatmaps.api.MapDifficulty
import io.beatmaps.common.PlaylistDifficulty
import io.beatmaps.maps.diffImg
import io.beatmaps.util.fcmemo
import react.Props
import react.dom.html.ReactHTML.span
import web.cssom.ClassName

external interface DiffIconsProps : Props {
    var diffs: List<MapDifficulty>?
    var selectedDifficulties: List<PlaylistDifficulty>?
    var onToggleDifficulty: ((PlaylistDifficulty) -> Unit)?
}

val diffIcons = fcmemo<DiffIconsProps>("diffIcons") { props ->
    props.diffs?.forEach { d ->
        val selection = PlaylistDifficulty(
            d.characteristic,
            d.difficulty
        )
        val selected = props.selectedDifficulties?.contains(selection) == true
        val editable = props.onToggleDifficulty != null

        span {
            className = ClassName(
                "badge rounded-pill badge-${d.difficulty.color}" +
                    (if (selected) " playlist-diff-selected" else "")
            )

            if (editable) {
                title = if (selected) {
                    "Remove from highlighted difficulties"
                } else {
                    "Highlight this difficulty"
                }

                tabIndex = 0

                onClick = {
                    props.onToggleDifficulty?.invoke(selection)
                }

                onKeyDown = {
                    if (it.key == "Enter" || it.key == " ") {
                        it.preventDefault()
                        props.onToggleDifficulty?.invoke(selection)
                    }
                }
            }
            diffImg {
                diff = d
            }
            +d.difficulty.human()
        }
    }
}
