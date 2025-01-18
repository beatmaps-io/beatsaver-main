package io.beatmaps.shared.map

import io.beatmaps.util.fcmemo
import react.Props
import react.dom.html.ReactHTML.span
import web.cssom.ClassName

external interface BotInfoProps : Props {
    var marginLeft: Boolean?
}

val botInfo = fcmemo<BotInfoProps>("botInfo") { props ->
    val margin = if (props.marginLeft != false) "ms-2" else "me-2"

    span {
        className = ClassName("badge rounded-pill badge-danger $margin")
        title = "Made using an AI"
        +"Bot"
    }
}
