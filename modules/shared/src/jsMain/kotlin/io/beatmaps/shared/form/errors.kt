package io.beatmaps.shared.form

import io.beatmaps.api.UploadValidationInfo
import io.beatmaps.upload.uploadError
import io.beatmaps.util.fcmemo
import js.objects.jso
import react.Props
import react.dom.html.ReactHTML.div
import web.cssom.ClassName
import web.cssom.Display

external interface ErrorProps : Props {
    var errors: List<String>?
    var validationErrors: List<UploadValidationInfo>?
    var valid: Boolean?
}

val errors = fcmemo<ErrorProps>("errors") { props ->
    props.errors?.forEach { error ->
        div {
            className = ClassName((if (props.valid == true) "" else "in") + "valid-feedback")
            style = jso {
                display = Display.block
            }
            +error
        }
    }
    props.validationErrors?.forEach {
        div {
            className = ClassName("invalid-feedback")
            style = jso {
                display = Display.block
            }
            uploadError {
                info = it
            }
        }
    }
}
