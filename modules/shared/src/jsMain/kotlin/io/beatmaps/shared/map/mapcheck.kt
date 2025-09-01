package io.beatmaps.shared.map
import io.beatmaps.util.fcmemo
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.span
import web.cssom.ClassName
import web.window.WindowTarget

external interface MapCheckProps : Props {
    var mapId: String
}

val mapcheck = fcmemo<MapCheckProps>("mapCheck") { props ->
    a {
        href = "https://kivalevan.me/BeatSaber-MapCheck/?id=${props.mapId}"
        target = WindowTarget._blank

        val text = "Map Check"
        title = text
        ariaLabel = text
        span {
            className = ClassName("dd-text")
            +text
        }
        i {
            className = ClassName("fas fa-search-location text-info")
        }
    }
}