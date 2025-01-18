package io.beatmaps.maps

import io.beatmaps.common.fixedStr
import io.beatmaps.util.fcmemo
import react.Props
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.tr
import web.cssom.ClassName

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

val score = fcmemo<ScoreProps>("score") { props ->
    tr {
        th {
            scope = "row"
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
        td {
            className = ClassName(props.scoreColor)
            +props.percentage
        }
        td {
            +props.pp.fixedStr(2)
        }
    }
}
