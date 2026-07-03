package io.beatmaps.admin.user

import io.beatmaps.util.fcmemo
import react.Props
import react.Ref
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.useState
import web.cssom.ClassName
import web.html.HTMLInputElement
import web.html.InputType

external interface SuspendDurationProps : Props {
    var durationRef: Ref<HTMLInputElement>
    var permanentRef: Ref<HTMLInputElement>
}

val suspendDuration = fcmemo<SuspendDurationProps>("suspendDuration") { props ->
    val (permanent, setPermanent) = useState(false)

    label {
        className = ClassName("form-label")
        htmlFor = "silence-duration"
        +"Length in minutes"
    }
    input {
        id = "silence-duration"
        className = ClassName("form-control")
        type = if (permanent) InputType.hidden else InputType.number
        min = "1"
        disabled = permanent
        defaultValue = "80"
        ref = props.durationRef
    }
    div {
        className = ClassName("form-check mt-3")
        input {
            id = "silence-permanent"
            className = ClassName("form-check-input")
            type = InputType.checkbox
            checked = permanent
            ref = props.permanentRef
            onChange = {
                setPermanent(it.currentTarget.checked)
            }
        }
        label {
            className = ClassName("form-check-label")
            htmlFor = "silence-permanent"
            +"Permanent"
        }
    }
}
