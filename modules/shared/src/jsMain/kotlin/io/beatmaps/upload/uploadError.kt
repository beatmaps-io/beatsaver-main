package io.beatmaps.upload

import io.beatmaps.api.UploadValidationInfo
import io.beatmaps.util.fcmemo
import react.Props
import react.dom.html.ReactHTML.a

external interface UploadErrorProps : Props {
    var info: UploadValidationInfo
}

val uploadError = fcmemo<UploadErrorProps>("uploadError") { props ->
    props.info.property.forEachIndexed { idx, it ->
        if (idx > 0) {
            +"."
        }

        it.descriptor?.also { d ->
            a {
                href = "#"

                onClick = { e ->
                    e.preventDefault()
                }
                title = it.name
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
