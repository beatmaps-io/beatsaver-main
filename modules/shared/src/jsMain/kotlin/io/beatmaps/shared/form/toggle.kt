package io.beatmaps.shared.form

import io.beatmaps.util.fcmemo
import org.w3c.dom.HTMLInputElement
import react.Props
import react.Ref
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import web.cssom.ClassName
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
        attrs.className = ClassName("form-check form-switch ${props.className ?: ""}")
        input {
            attrs.type = InputType.checkbox
            attrs.className = ClassName("form-check-input")
            attrs.id = props.id
            attrs.defaultChecked = props.default ?: false
            attrs.disabled = props.disabled ?: false
            ref = props.toggleRef
            attrs.onChange = { ev ->
                props.block?.invoke(ev.target.checked)
            }
        }
        label {
            attrs.className = ClassName("form-check-label")
            attrs.htmlFor = props.id
            +props.text
        }
    }
}
