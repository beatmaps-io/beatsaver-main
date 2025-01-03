package io.beatmaps.index
import kotlinx.html.title
import react.Props
import react.dom.a
import react.dom.i
import react.dom.span
import react.fc
import kotlin.collections.set

external interface OneClickProps : Props {
    var mapId: String
}

val oneclick = fc<OneClickProps>("oneClick") { props ->
    a("beatsaver://${props.mapId}") {
        val text = "One-Click"
        attrs.title = text
        attrs.attributes["aria-label"] = text
        span("dd-text") { +text }
        i("fas fa-cloud-download-alt text-info") { }
    }
}
