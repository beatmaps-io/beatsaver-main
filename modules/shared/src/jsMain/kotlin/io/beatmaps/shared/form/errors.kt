package io.beatmaps.shared.form

import io.beatmaps.api.UploadValidationInfo
import io.beatmaps.upload.uploadError
import react.Props
import react.dom.div
import react.dom.jsStyle
import react.fc

external interface ErrorProps : Props {
    var errors: List<String>?
    var validationErrors: List<UploadValidationInfo>?
    var valid: Boolean?
}

val errors = fc<ErrorProps>("errors") { props ->
    props.errors?.forEach { error ->
        div((if (props.valid == true) "" else "in") + "valid-feedback") {
            attrs.jsStyle {
                display = "block"
            }
            +error
        }
    }
    props.validationErrors?.forEach {
        div("invalid-feedback") {
            attrs.jsStyle {
                display = "block"
            }
            uploadError {
                attrs.info = it
            }
        }
    }
}
