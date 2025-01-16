package io.beatmaps.shared

import io.beatmaps.util.fcmemo
import react.createElement
import react.dom.img
import react.dom.p

val loadingElem = createElement(
    fcmemo("Loading") {
        p("text-center") {
            img("Loading", "/static/loading.svg") {
                attrs.width = "24"
                attrs.height = "24"
            }
        }
    }
)
