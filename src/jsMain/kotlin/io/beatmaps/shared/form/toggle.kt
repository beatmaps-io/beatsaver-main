package io.beatmaps.shared.form

import external.reactFor
import kotlinx.html.InputType
import kotlinx.html.id
import kotlinx.html.js.onChangeFunction
import org.w3c.dom.HTMLInputElement
import react.PropsWithRef
import react.dom.div
import react.dom.input
import react.dom.label
import react.forwardRef

external interface ToggleProps : PropsWithRef<HTMLInputElement> {
    var id: String
    var text: String
    var disabled: Boolean?
    var block: ((Boolean) -> Unit)?
    var default: Boolean?
    var className: String?
}

val toggle = forwardRef<HTMLInputElement, ToggleProps> { props, refPassed ->
    div("form-check form-switch ${props.className ?: ""}") {
        input(InputType.checkBox, classes = "form-check-input") {
            attrs.id = props.id
            attrs.defaultChecked = props.default ?: false
            attrs.disabled = props.disabled ?: false
            ref = refPassed
            attrs.onChangeFunction = { ev ->
                props.block?.invoke((ev.target as HTMLInputElement).checked)
            }
        }
        label("form-check-label") {
            attrs.reactFor = props.id
            +props.text
        }
    }
}
