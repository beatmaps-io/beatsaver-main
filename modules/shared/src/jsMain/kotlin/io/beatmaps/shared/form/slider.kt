package io.beatmaps.shared.form

import external.ReactSlider
import react.Props
import react.dom.html.ReactHTML.div
import react.fc
import web.cssom.ClassName

external interface SliderProps : Props {
    var text: String
    var currentMin: Float
    var currentMax: Float
    var max: Int?
    var block: (Array<Int>) -> Unit
    var className: ClassName?
}

val slider = fc<SliderProps>("slider") { props ->
    val max = props.max ?: 16

    div {
        attrs.className = props.className

        val maxSlider = max * 10
        ReactSlider.default {
            attrs.ariaLabel = arrayOf("Min ${props.text}", "Max ${props.text}")
            attrs.value = arrayOf((props.currentMin * 10).toInt(), (props.currentMax * 10).toInt())
            attrs.max = maxSlider
            attrs.defaultValue = arrayOf(0, maxSlider)
            attrs.minDistance = 5
            attrs.onChange = props.block
        }
        div {
            attrs.className = ClassName("row")
            div {
                attrs.className = ClassName("col")
                +props.text
            }
            div {
                attrs.className = ClassName("col text-end")
                val maxStr = if (props.currentMax >= max) "∞" else props.currentMax.toString()
                +"${props.currentMin} - $maxStr"
            }
        }
    }
}
