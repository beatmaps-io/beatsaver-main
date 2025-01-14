package io.beatmaps.shared

import react.createElement
import react.dom.i
import react.dom.p
import react.fc

val loadingElem = createElement(
    fc {
        p {
            i {
                +"Loading..."
            }
        }
    }
)
