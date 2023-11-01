package io.beatmaps.shared.map

import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapVersion
import kotlinx.html.title
import react.Props
import react.dom.a
import react.dom.i
import react.fc

external interface DownloadProps : Props {
    var map: MapDetail
    var version: MapVersion
}

val downloadZip = fc<DownloadProps> { props ->
    a(props.version.downloadURL) {
        attrs.rel = "noopener"
        attrs.title = "Download zip"
        attrs.attributes["aria-label"] = "Download zip"
        i("fas fa-download text-info") {
            attrs.attributes["aria-hidden"] = "true"
        }
    }
}
