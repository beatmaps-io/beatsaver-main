package io.beatmaps.maps

import io.beatmaps.common.fixedStr
import kotlinx.html.ThScope
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
import react.dom.td
import react.dom.th
import react.dom.tr

data class ScoreProps(var position: Int, var playerId: Long, var name: String, var pp: Double, var score: Int, var scoreColor: String, var percentage: String, var mods: List<String>) : RProps

class Score : RComponent<ScoreProps, RState>() {
    override fun RBuilder.render() {
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
}

fun RBuilder.score(handler: ScoreProps.() -> Unit): ReactElement {
    return child(Score::class) {
        this.attrs(handler)
    }
}
