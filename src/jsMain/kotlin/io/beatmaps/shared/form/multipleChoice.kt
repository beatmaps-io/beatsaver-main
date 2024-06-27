package io.beatmaps.shared.form

import external.reactFor
import kotlinx.html.InputType
import kotlinx.html.id
import kotlinx.html.js.onChangeFunction
import org.w3c.dom.HTMLInputElement
import react.PropsWithRef
import react.RBuilder
import react.RComponent
import react.State
import react.dom.div
import react.dom.input
import react.dom.label

external interface MultipleChoiceProps<T> : PropsWithRef<HTMLInputElement> {
    var name: String
    var choices: Map<String, T>
    var selectedValue: T
    var block: ((T) -> Unit)?
    var className: String?
}

class MultipleChoice<T> : RComponent<MultipleChoiceProps<T>, State>() {
    override fun RBuilder.render() {
        div("multiple-choice ${props.className ?: ""}") {
            props.choices.forEach { (text, value) ->
                val id = "${props.name}:${text.lowercase()}"

                input(InputType.radio, classes = "form-check-input") {
                    attrs.id = id
                    attrs.name = props.name
                    attrs.checked = value == props.selectedValue
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
}

fun <T> RBuilder.multipleChoice(choices: Map<String, T>, handler: MultipleChoiceProps<T>.() -> Unit) {
    return child((MultipleChoice<T>())::class) {
        attrs.choices = choices
        this.attrs(handler)
    }
}
