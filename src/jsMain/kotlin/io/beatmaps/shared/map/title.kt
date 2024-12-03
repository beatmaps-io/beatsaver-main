package io.beatmaps.shared.map

import external.routeLink
import kotlinx.browser.window
import react.Props
import react.fc
import web.window.WindowTarget

external interface MapTitleProps : Props {
    var title: String
    var mapKey: String
}

val mapTitle = fc<MapTitleProps> {
    val target = if (window.top == window.self) null else WindowTarget._top
    routeLink("/maps/${it.mapKey}", target = target) {
        +it.title.ifBlank {
            "<NO NAME>"
        }
    }
}
