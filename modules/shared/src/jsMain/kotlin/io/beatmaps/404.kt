package io.beatmaps

import io.beatmaps.util.fcmemo
import kotlinx.html.id
import react.Props
import react.dom.div
import react.useEffectOnce

val notFound = fcmemo<Props>("notFound") {
    useEffectOnce {
        setPageTitle("Page Not Found")
    }

    div {
        attrs.id = "notfound"
        +"Not found"
    }
}
