package io.beatmaps.shared.form

import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.fc
import web.cssom.ClassName
import web.html.InputType

external interface MultipleChoiceProps<T> : Props {
    var name: String
    var choices: Map<String, T>
    var selectedValue: T
    var block: ((T) -> Unit)?
    var className: String?
}

val multipleChoice = fc<MultipleChoiceProps<Any?>>("multipleChoice") { props ->
    div {
        attrs.className = ClassName("multiple-choice ${props.className ?: ""}")
        props.choices.forEach { (text, value) ->
            val id = "${props.name}:${text.lowercase()}"

            input {
                key = id
                attrs.type = InputType.radio
                attrs.className = ClassName("form-check-input")
                attrs.id = id
                attrs.name = props.name
                attrs.checked = value == props.selectedValue
                attrs.onChange = {
                    props.block?.invoke(value)
                }
            }
            label {
                attrs.className = ClassName("form-check-label")
                attrs.htmlFor = id
                +text
            }
        }
    }
}
