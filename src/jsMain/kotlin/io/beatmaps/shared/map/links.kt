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
import react.fc
import react.useContext

external interface LinksProps : Props {
    var map: MapDetail
    var version: MapVersion?
}

val links = fc<LinksProps> { props ->
    val modal = useContext(modalContext)

    copyBsr {
        attrs.map = props.map
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
        attrs.title = "Preview"
        attrs.attributes["aria-label"] = "Preview"
        attrs.onClickFunction = {
            it.preventDefault()

            if (props.version?.state == EMapState.Published) {
                modal?.current?.showById(props.map.id)
            } else {
                props.version?.hash?.let { hash ->
                    modal?.current?.show(hash)
                }
            }
        }
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
