package io.beatmaps

import kotlinx.html.id
import react.Props
import react.dom.div
import react.fc

val notFound = fc<Props> {
    div {
        attrs.id = "notfound"
        +"Not found"
    }
}
