package io.beatmaps

import kotlinx.html.id
import react.RProps
import react.dom.div
import react.functionComponent

val notFound = functionComponent<RProps> {
    div {
        attrs.id = "notfound"
        +"Not found"
    }
}
