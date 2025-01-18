package io.beatmaps.shared.map

import external.routeLink
import io.beatmaps.util.fcmemo
import react.Props
import web.window.WindowTarget
import web.window.window

external interface MapTitleProps : Props {
    var title: String
    var mapKey: String
}

val mapTitle = fcmemo<MapTitleProps>("mapTitle") {
    val target = if (window.top === window) null else WindowTarget._top
    routeLink("/maps/${it.mapKey}", target = target) {
        +it.title.ifBlank {
            "<NO NAME>"
        }
    }
}
