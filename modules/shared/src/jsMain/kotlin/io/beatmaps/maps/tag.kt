package io.beatmaps.maps

import io.beatmaps.common.MapTag
import js.objects.jso
import react.Props
import react.dom.events.MouseEventHandler
import react.dom.html.ReactHTML.span
import react.fc
import web.cssom.ClassName
import web.cssom.number
import web.html.HTMLElement

external interface MapTagProps : Props {
    var selected: Boolean
    var excluded: Boolean
    var margins: String?
    var tag: MapTag
    var onClick: MouseEventHandler<HTMLElement>
}

val mapTag = fc<MapTagProps>("mapTag") { props ->
    val dark = !props.selected && !props.excluded
    val margins = props.margins ?: "me-2 mb-2"
    span {
        attrs.className = ClassName("badge badge-${if (props.excluded) "danger" else props.tag.type.color} $margins")
        attrs.style = jso {
            opacity = number(if (dark) 0.4 else 1.0)
        }
        attrs.title = props.tag.human
        attrs.onClick = props.onClick
        +props.tag.human
    }
}
