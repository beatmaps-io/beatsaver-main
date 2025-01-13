package io.beatmaps.shared.map

import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapVersion
import io.beatmaps.common.api.EMapState
import io.beatmaps.index.modalContext
import io.beatmaps.index.oneclick
import io.beatmaps.previewBaseUrl
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import react.Props
import react.dom.a
import react.dom.i
import react.dom.span
import react.fc
import react.useContext

external interface LinksProps : Props {
    var map: MapDetail
    var version: MapVersion?
    var limited: Boolean?
}

val links = fc<LinksProps>("links") { props ->
    val modal = useContext(modalContext)

    copyBsr {
        attrs.map = props.map
    }
    if (props.limited != true) {
        copyEmbed {
            attrs.map = props.map
        }
    }
    val version = props.version
    val altLink = if (version?.state == EMapState.Published) {
        "$previewBaseUrl?id=${props.map.id}"
    } else if (version != null) {
        "/maps/viewer/${version.hash}"
    } else {
        "#"
    }
    a(altLink) {
        val text = "Preview"
        attrs.title = text
        attrs.attributes["aria-label"] = text
        attrs.target = "_top"
        attrs.onClickFunction = {
            if (modal?.current != null) it.preventDefault()

            if (props.version?.state == EMapState.Published) {
                modal?.current?.showById?.invoke(props.map.id)
            } else {
                props.version?.hash?.let { hash ->
                    modal?.current?.show?.invoke(hash)
                }
            }
        }
        span("dd-text") { +text }
        i("fas fa-play text-info") {
            attrs.attributes["aria-hidden"] = "true"
        }
    }
    oneclick {
        attrs.mapId = props.map.id
    }
    props.version?.let { v ->
        downloadZip {
            attrs.map = props.map
            attrs.version = v
        }
    }
}
