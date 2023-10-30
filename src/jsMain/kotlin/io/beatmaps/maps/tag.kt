package io.beatmaps.maps

import io.beatmaps.common.MapTag
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import org.w3c.dom.events.Event
import react.Props
import react.dom.jsStyle
import react.dom.span
import react.fc

external interface MapTagProps : Props {
    var selected: Boolean
    var excluded: Boolean
    var margins: String?
    var tag: MapTag
    var onClick: (Event) -> Unit
}

val mapTag = fc<MapTagProps> { props ->
    val dark = !props.selected && !props.excluded
    val margins = props.margins ?: "me-2 mb-2"
    span("badge badge-${if (props.excluded) "danger" else props.tag.type.color} $margins") {
        attrs.jsStyle {
            opacity = if (dark) 0.4 else 1
        }
        attrs.title = props.tag.human
        attrs.onClickFunction = props.onClick
        +props.tag.human
    }
}
