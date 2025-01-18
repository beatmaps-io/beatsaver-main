package io.beatmaps.shared.map
import io.beatmaps.util.fcmemo
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.span
import web.cssom.ClassName

external interface OneClickProps : Props {
    var mapId: String
}

val oneclick = fcmemo<OneClickProps>("oneClick") { props ->
    a {
        href = "beatsaver://${props.mapId}"
        val text = "One-Click"
        title = text
        ariaLabel = text
        span {
            className = ClassName("dd-text")
            +text
        }
        i {
            className = ClassName("fas fa-cloud-download-alt text-info")
        }
    }
}
