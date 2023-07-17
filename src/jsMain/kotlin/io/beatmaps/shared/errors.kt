package io.beatmaps.shared

import react.Props
import react.dom.div
import react.dom.jsStyle
import react.fc

external interface ErrorProps : Props {
    var errors: List<String>
    var valid: Boolean?
}

val errors = fc<ErrorProps> { props ->
    props.errors.forEach { error ->
        div((if (props.valid == true) "" else "in") + "valid-feedback") {
            attrs.jsStyle {
                display = "block"
            }
            +error
        }
    }
}
