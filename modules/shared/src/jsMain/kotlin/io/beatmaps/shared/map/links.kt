package io.beatmaps.shared.map

import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapVersion
import io.beatmaps.common.api.EMapState
import io.beatmaps.previewBaseUrl
import io.beatmaps.shared.modalContext
import io.beatmaps.util.fcmemo
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.span
import react.use
import web.cssom.ClassName
import web.window.WindowTarget

external interface LinksProps : Props {
    var map: MapDetail
    var version: MapVersion?
    var limited: Boolean?
}

val links = fcmemo<LinksProps>("links") { props ->
    val modal = use(modalContext)

    if (props.limited != true) {
        mapcheck {
            mapId = props.map.id
        }
    }
    copyBsr {
        map = props.map
    }
    if (props.limited != true) {
        copyEmbed {
            map = props.map
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
    a {
        href = altLink
        val text = "Preview"
        title = text
        ariaLabel = text
        target = WindowTarget._top
        onClick = {
            if (modal?.current != null) it.preventDefault()

            if (props.version?.state == EMapState.Published) {
                modal?.current?.showById?.invoke(props.map.id)
            } else {
                props.version?.hash?.let { hash ->
                    modal?.current?.show?.invoke(hash)
                }
            }
        }
        span {
            className = ClassName("dd-text")
            +text
        }
        i {
            className = ClassName("fas fa-play text-info")
            ariaHidden = true
        }
    }
    oneclick {
        mapId = props.map.id
    }
    props.version?.let { v ->
        downloadZip {
            map = props.map
            this.version = v
        }
    }
}
