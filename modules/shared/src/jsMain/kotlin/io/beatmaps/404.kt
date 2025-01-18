package io.beatmaps

import io.beatmaps.util.fcmemo
import react.Props
import react.dom.html.ReactHTML.div
import react.useEffectOnce

val notFound = fcmemo<Props>("notFound") {
    useEffectOnce {
        setPageTitle("Page Not Found")
    }

    div {
        id = "notfound"
        +"Not found"
    }
}
