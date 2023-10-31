package io.beatmaps.index
import kotlinx.html.title
import react.Props
import react.dom.a
import react.dom.i
import react.fc
import kotlin.collections.set

external interface OneClickProps : Props {
    var mapId: String
}

val oneclick = fc<OneClickProps> { props ->
    a("beatsaver://${props.mapId}") {
        attrs.title = "One-Click"
        attrs.attributes["aria-label"] = "One-Click"
        i("fas fa-cloud-download-alt text-info") { }
    }
}
