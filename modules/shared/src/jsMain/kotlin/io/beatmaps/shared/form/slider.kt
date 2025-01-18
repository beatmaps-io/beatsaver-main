package io.beatmaps.shared.form

import external.ReactSlider
import io.beatmaps.util.fcmemo
import react.Props
import react.dom.html.ReactHTML.div
import web.cssom.ClassName

external interface SliderProps : Props {
    var text: String
    var currentMin: Float
    var currentMax: Float
    var max: Int?
    var block: (Array<Int>) -> Unit
    var className: ClassName?
}

val slider = fcmemo<SliderProps>("slider") { props ->
    val max = props.max ?: 16

    div {
        className = props.className

        val maxSlider = max * 10
        ReactSlider.default {
            ariaLabel = arrayOf("Min ${props.text}", "Max ${props.text}")
            value = arrayOf((props.currentMin * 10).toInt(), (props.currentMax * 10).toInt())
            this.max = maxSlider
            defaultValue = arrayOf(0, maxSlider)
            minDistance = 5
            onChange = props.block
        }
        div {
            className = ClassName("row")
            div {
                className = ClassName("col")
                +props.text
            }
            div {
                className = ClassName("col text-end")
                val maxStr = if (props.currentMax >= max) "∞" else props.currentMax.toString()
                +"${props.currentMin} - $maxStr"
            }
        }
    }
}
