package io.beatmaps.shared.map

import external.routeLink
import react.Props
import react.fc
import web.window.WindowTarget

external interface MapTitleProps : Props {
    var title: String
    var mapKey: String
}

val mapTitle = fc<MapTitleProps> {
    routeLink("/maps/${it.mapKey}", target = WindowTarget._top) {
        +it.title.ifBlank {
            "<NO NAME>"
        }
    }
}
