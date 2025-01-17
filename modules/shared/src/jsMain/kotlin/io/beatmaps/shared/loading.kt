package io.beatmaps.shared

import io.beatmaps.util.fcmemo
import react.createElement
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.p
import web.cssom.ClassName

val loadingElem = createElement(
    fcmemo("Loading") {
        p {
            attrs.className = ClassName("text-center")
            img {
                attrs.alt = "Loading"
                attrs.src = "/static/loading.svg"
                attrs.width = 24.0
                attrs.height = 24.0
            }
        }
    }
)
