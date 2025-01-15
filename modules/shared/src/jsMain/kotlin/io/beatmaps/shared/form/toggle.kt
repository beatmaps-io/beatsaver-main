package io.beatmaps.shared.form

import external.reactFor
import io.beatmaps.util.fcmemo
import kotlinx.html.InputType
import kotlinx.html.id
import kotlinx.html.js.onChangeFunction
import org.w3c.dom.HTMLInputElement
import react.Props
import react.Ref
import react.dom.div
import react.dom.input
import react.dom.label

external interface ToggleProps : Props {
    var id: String
    var text: String
    var disabled: Boolean?
    var block: ((Boolean) -> Unit)?
    var default: Boolean?
    var className: String?
    var toggleRef: Ref<HTMLInputElement>?
}

val toggle = fcmemo<ToggleProps>("toggle") { props ->
    div("form-check form-switch ${props.className ?: ""}") {
        input(InputType.checkBox, classes = "form-check-input") {
            attrs.id = props.id
            attrs.defaultChecked = props.default ?: false
            attrs.disabled = props.disabled ?: false
            ref = props.toggleRef
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
