package io.beatmaps.shared

import io.beatmaps.util.fcmemo
import react.createElement
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.p
import web.cssom.ClassName

val loadingElem = createElement(
    fcmemo("Loading") {
        p {
            className = ClassName("text-center")
            img {
                alt = "Loading"
                src = "/static/loading.svg"
                width = 24.0
                height = 24.0
            }
        }
    }
)
