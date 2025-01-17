package io.beatmaps.upload

import io.beatmaps.api.UploadValidationInfo
import react.Props
import react.dom.html.ReactHTML.a
import react.fc

external interface UploadErrorProps : Props {
    var info: UploadValidationInfo
}

val uploadError = fc<UploadErrorProps>("uploadError") { props ->
    props.info.property.forEachIndexed { idx, it ->
        if (idx > 0) {
            +"."
        }

        it.descriptor?.also { d ->
            a {
                attrs.href = "#"

                attrs.onClick = { e ->
                    e.preventDefault()
                }
                attrs.title = it.name
                +d
            }
        } ?: run {
            +it.name
        }
        if (it.index != null) {
            +"[${it.index}]"
        }
    }
    if (props.info.property.isNotEmpty()) {
        +": "
    }
    +props.info.message
}
