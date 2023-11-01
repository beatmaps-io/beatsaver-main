package io.beatmaps.shared.map

import io.beatmaps.api.MapVersion
import kotlinx.html.title
import react.Props
import react.dom.span
import react.fc

external interface BotInfoProps : Props {
    var automapper: Boolean?
    var version: MapVersion?
    var marginLeft: Boolean?
}

val botInfo = fc<BotInfoProps> { props ->
    val score = (props.version?.sageScore ?: 0)
    val margin = if (props.marginLeft != false) "ms-2" else "me-2"

    fun renderBadge(color: String, title: String, text: String) =
        span("badge rounded-pill badge-$color $margin") {
            attrs.title = title
            +text
        }

    if (score < -4 || props.automapper == true) {
        renderBadge("danger", "Made by a bot", "Bot")
    } else if (score < 0) {
        renderBadge("unsure", "Could be a bot", "Unsure")
    }
}
