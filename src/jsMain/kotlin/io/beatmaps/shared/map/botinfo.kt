package io.beatmaps.shared.map

import kotlinx.html.title
import react.Props
import react.dom.span
import react.fc

external interface BotInfoProps : Props {
    var marginLeft: Boolean?
}

val botInfo = fc<BotInfoProps> { props ->
    val margin = if (props.marginLeft != false) "ms-2" else "me-2"

    span("badge rounded-pill badge-danger $margin") {
        attrs.title = "Made using an AI"
        +"Bot"
    }
}
