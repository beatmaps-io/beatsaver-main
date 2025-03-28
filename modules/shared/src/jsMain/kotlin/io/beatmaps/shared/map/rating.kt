package io.beatmaps.shared.map

import io.beatmaps.util.fcmemo
import js.objects.jso
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.small
import web.cssom.ClassName
import web.cssom.number
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
        small {
            className = ClassName("text-center vote")
            div {
                className = ClassName("u")
                style = jso {
                    flex = number(it.up.toDouble())
                }
            }
            div {
                className = ClassName("o")
                style = jso {
                    flex = number(if (totalVotes < 1) 1.0 else (uncertainty * totalVotes / (1 - uncertainty)))
                }
            }
            div {
                className = ClassName("d")
                style = jso {
                    flex = number(it.down.toDouble())
                }
            }
        }
        div {
            className = ClassName("percentage")
            title = "${it.up}/${it.down}"
            +"${it.rating}%"
        }
    }
}
