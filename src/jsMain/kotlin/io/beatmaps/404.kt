package io.beatmaps

import kotlinx.html.id
import react.Props
import react.dom.div
import react.functionComponent

val notFound = functionComponent<Props> {
    div {
        attrs.id = "notfound"
        +"Not found"
    }
}
