package io.beatmaps

import kotlinx.html.id
import react.Props
import react.dom.div
import react.fc
import react.useEffectOnce

val notFound = fc<Props>("notFound") {
    useEffectOnce {
        setPageTitle("Page Not Found")
    }

    div {
        attrs.id = "notfound"
        +"Not found"
    }
}
