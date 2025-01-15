package io.beatmaps.shared.map

import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapVersion
import io.beatmaps.util.fcmemo
import kotlinx.html.title
import react.Props
import react.dom.a
import react.dom.i
import react.dom.span

external interface DownloadProps : Props {
    var map: MapDetail
    var version: MapVersion
}

val downloadZip = fcmemo<DownloadProps>("downloadZip") { props ->
    a(props.version.downloadURL) {
        val text = "Download zip"
        attrs.rel = "noopener"
        attrs.title = text
        attrs.attributes["aria-label"] = text
        span("dd-text") { +text }
        i("fas fa-download text-info") {
            attrs.attributes["aria-hidden"] = "true"
        }
    }
}
