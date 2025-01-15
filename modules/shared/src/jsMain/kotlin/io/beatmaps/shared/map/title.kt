package io.beatmaps.shared.map

import external.routeLink
import io.beatmaps.util.fcmemo
import kotlinx.browser.window
import react.Props
import web.window.WindowTarget

external interface MapTitleProps : Props {
    var title: String
    var mapKey: String
}

val mapTitle = fcmemo<MapTitleProps>("mapTitle") {
    val target = if (window.top === window.self) null else WindowTarget._top
    routeLink("/maps/${it.mapKey}", target = target) {
        +it.title.ifBlank {
            "<NO NAME>"
        }
    }
}
