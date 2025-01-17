package io.beatmaps.shared.form

import io.beatmaps.api.UploadValidationInfo
import io.beatmaps.upload.uploadError
import js.objects.jso
import react.Props
import react.dom.html.ReactHTML.div
import react.fc
import web.cssom.ClassName
import web.cssom.Display

external interface ErrorProps : Props {
    var errors: List<String>?
    var validationErrors: List<UploadValidationInfo>?
    var valid: Boolean?
}

val errors = fc<ErrorProps>("errors") { props ->
    props.errors?.forEach { error ->
        div {
            attrs.className = ClassName((if (props.valid == true) "" else "in") + "valid-feedback")
            attrs.style = jso {
                display = Display.block
            }
            +error
        }
    }
    props.validationErrors?.forEach {
        div {
            attrs.className = ClassName("invalid-feedback")
            attrs.style = jso {
                display = Display.block
            }
            uploadError {
                attrs.info = it
            }
        }
    }
}
