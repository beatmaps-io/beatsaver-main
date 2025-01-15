package io.beatmaps.shared.map

import io.beatmaps.util.fcmemo
import kotlinx.html.title
import react.Props
import react.dom.div
import react.dom.jsStyle
import react.dom.small
import kotlin.math.log
import kotlin.math.pow

external interface RatingProps : Props {
    var up: Int
    var down: Int
    var rating: Float
}

val rating = fcmemo<RatingProps>("rating") {
    val totalVotes = (it.up + it.down).toDouble()
    var uncertainty = 2.0.pow(-log(totalVotes / 2 + 1, 3.0))
    val weightedRange = 25.0
    val weighting = 2
    if ((totalVotes + weighting) < weightedRange) {
        uncertainty += (1 - uncertainty) * (1 - (totalVotes + weighting) * (1 / weightedRange))
    }

    div {
        small("text-center vote") {
            div("u") {
                attrs.jsStyle {
                    flex = it.up
                }
            }
            div("o") {
                attrs.jsStyle {
                    flex = if (totalVotes < 1) 1 else (uncertainty * totalVotes / (1 - uncertainty))
                }
            }
            div("d") {
                attrs.jsStyle {
                    flex = it.down
                }
            }
        }
        div("percentage") {
            attrs.title = "${it.up}/${it.down}"
            +"${it.rating}%"
        }
    }
}
