package io.beatmaps.shared.form

import external.reactFor
import kotlinx.html.INPUT
import kotlinx.html.InputType
import kotlinx.html.id
import kotlinx.html.js.onChangeFunction
import react.Props
import react.dom.RDOMBuilder
import react.dom.div
import react.dom.input
import react.dom.label
import react.fc

external interface MultipleChoiceProps<T> : Props {
    var name: String
    var choices: Map<String, T>
    var selectedValue: T
    var block: ((T) -> Unit)?
    var className: String?
}

fun RDOMBuilder<INPUT>.betterChecked(b: Boolean) {
    attrs.attributes["checked"] = if (b) "checked" else ""
}

val multipleChoice = fc<MultipleChoiceProps<Any?>>("multipleChoice") { props ->
    div("multiple-choice ${props.className ?: ""}") {
        props.choices.forEach { (text, value) ->
            val id = "${props.name}:${text.lowercase()}"

            input(InputType.radio, classes = "form-check-input") {
                key = id
                attrs.id = id
                attrs.name = props.name
                // If you use the checked option react gets upset that the controlled state changes
                betterChecked(value == props.selectedValue)
                attrs.onChangeFunction = {
                    props.block?.invoke(value)
                }
            }
            label("form-check-label") {
                attrs.reactFor = id
                +text
            }
        }
    }
}
