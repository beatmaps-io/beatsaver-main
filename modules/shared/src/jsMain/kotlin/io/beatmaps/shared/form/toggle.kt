package io.beatmaps.shared.form

import io.beatmaps.util.fcmemo
import react.Props
import react.Ref
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import web.cssom.ClassName
import web.html.HTMLInputElement
import web.html.InputType

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
    div {
        className = ClassName("form-check form-switch ${props.className ?: ""}")
        input {
            type = InputType.checkbox
            className = ClassName("form-check-input")
            id = props.id
            defaultChecked = props.default ?: false
            disabled = props.disabled ?: false
            ref = props.toggleRef
            onChange = { ev ->
                props.block?.invoke(ev.target.checked)
            }
        }
        label {
            className = ClassName("form-check-label")
            htmlFor = props.id
            +props.text
        }
    }
}
