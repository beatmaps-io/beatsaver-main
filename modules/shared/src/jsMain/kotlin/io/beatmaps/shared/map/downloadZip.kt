package io.beatmaps.shared.map

import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapVersion
import io.beatmaps.util.fcmemo
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.span
import web.cssom.ClassName

external interface DownloadProps : Props {
    var map: MapDetail
    var version: MapVersion
}

val downloadZip = fcmemo<DownloadProps>("downloadZip") { props ->
    a {
        href = props.version.downloadURL
        val text = "Download zip"
        rel = "noopener"
        title = text
        ariaLabel = text
        span {
            className = ClassName("dd-text")
            +text
        }
        i {
            className = ClassName("fas fa-download text-info")
            ariaHidden = true
        }
    }
}
