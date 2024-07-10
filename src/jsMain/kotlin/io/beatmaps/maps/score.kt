package io.beatmaps.maps

import io.beatmaps.common.fixedStr
import kotlinx.html.ThScope
import react.Props
import react.dom.td
import react.dom.th
import react.dom.tr
import react.fc

external interface ScoreProps : Props {
    var position: Int
    var playerId: Long?
    var name: String
    var pp: Double
    var score: Int
    var scoreColor: String
    var percentage: String
    var mods: List<String>
}

val score = fc<ScoreProps> { props ->
    tr {
        th(scope = ThScope.row) {
            +"${props.position}"
        }
        td {
            +props.name
        }
        td {
            +(props.score.asDynamic().toLocaleString() as String)
        }
        td {
            +if (props.mods.isEmpty()) {
                "-"
            } else {
                props.mods.joinToString(", ")
            }
        }
        td(props.scoreColor) {
            +props.percentage
        }
        td {
            +props.pp.fixedStr(2)
        }
    }
}
