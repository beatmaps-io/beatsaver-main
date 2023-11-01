package io.beatmaps.shared.map

import external.routeLink
import react.Props
import react.fc

external interface MapTitleProps : Props {
    var title: String
    var mapKey: String
}

val mapTitle = fc<MapTitleProps> {
    routeLink("/maps/${it.mapKey}") {
        +it.title.ifBlank {
            "<NO NAME>"
        }
    }
}
