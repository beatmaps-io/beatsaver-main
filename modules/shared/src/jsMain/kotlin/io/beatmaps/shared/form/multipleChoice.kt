package io.beatmaps.shared.form

import io.beatmaps.util.fcmemo
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import web.cssom.ClassName
import web.html.InputType

external interface MultipleChoiceProps<T> : Props {
    var name: String
    var choices: Map<String, T>
    var selectedValue: T
    var block: ((T) -> Unit)?
    var className: String?
}

val multipleChoice = fcmemo<MultipleChoiceProps<Any?>>("multipleChoice") { props ->
    div {
        className = ClassName("multiple-choice ${props.className ?: ""}")
        props.choices.forEach { (text, value) ->
            val id = "${props.name}-${text.lowercase()}"

            input {
                key = id
                type = InputType.radio
                className = ClassName("form-check-input")
                this.id = id
                name = props.name
                checked = value == props.selectedValue
                onChange = {
                    props.block?.invoke(value)
                }
            }
            label {
                className = ClassName("form-check-label")
                htmlFor = id
                +text
            }
        }
    }
}
