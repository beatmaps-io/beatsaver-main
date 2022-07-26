package io.beatmaps.index

import external.TimeAgo
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapDifficulty
import io.beatmaps.api.MapVersion
import io.beatmaps.common.api.EMapState
import io.beatmaps.maps.diffImg
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import react.RProps
import react.RReadableRef
import react.dom.a
import react.dom.div
import react.dom.i
import react.dom.span
import react.functionComponent
import react.router.dom.routeLink
import kotlin.collections.set

external interface BotInfoProps : RProps {
    var automapper: Boolean?
    var version: MapVersion?
    var marginLeft: Boolean?
}

val botInfo = functionComponent<BotInfoProps> { props ->
    val score = (props.version?.sageScore ?: 0)
    val margin = if (props.marginLeft != false) "ms-2" else "me-2"

    fun renderBadge(color: String, title: String, text: String) =
        span("badge rounded-pill badge-$color $margin") {
            attrs.title = title
            +text
        }

    if (score < -4 || props.automapper == true) {
        renderBadge("danger", "Made by a bot", "Bot")
    } else if (score < 0) {
        renderBadge("unsure", "Could be a bot", "Unsure")
    }
}

external interface DiffIconsProps : RProps {
    var diffs: List<MapDifficulty>?
}

val diffIcons = functionComponent<DiffIconsProps> { props ->
    props.diffs?.forEach { d ->
        span("badge rounded-pill badge-${d.difficulty.color}") {
            diffImg(d)
            +d.difficulty.human()
        }
    }
}

external interface DownloadProps : RProps {
    var map: MapDetail
    var version: MapVersion
}

val downloadZip = functionComponent<DownloadProps> { props ->
    a(props.version.downloadURL) {
        attrs.rel = "noopener"
        attrs.title = "Download zip"
        attrs.attributes["aria-label"] = "Download zip"
        i("fas fa-download text-info") {
            attrs.attributes["aria-hidden"] = "true"
        }
    }
}

external interface CopyBSRProps : RProps {
    var map: MapDetail
}

val copyBsr = functionComponent<CopyBSRProps> { props ->
    a("#") {
        attrs.title = "Copy BSR"
        attrs.attributes["aria-label"] = "Copy BSR"
        attrs.onClickFunction = {
            it.preventDefault()
            setClipboard("!bsr ${props.map.id}")
        }
        i("fab fa-twitch text-info") {
            attrs.attributes["aria-hidden"] = "true"
        }
    }
}

fun setClipboard(str: String) {
    val tempElement = document.createElement("span")
    tempElement.textContent = str
    document.body?.appendChild(tempElement)
    val selection = window.asDynamic().getSelection()
    val range = window.document.createRange()
    selection.removeAllRanges()
    range.selectNode(tempElement)
    selection.addRange(range)
    window.document.execCommand("copy")
    selection.removeAllRanges()
    window.document.body?.removeChild(tempElement)
}

external interface LinksProps : RProps {
    var map: MapDetail
    var version: MapVersion?
    var modal: RReadableRef<ModalComponent>
}

val links = functionComponent<LinksProps> { props ->
    copyBsr {
        attrs.map = props.map
    }
    val version = props.version
    val altLink = if (version?.state == EMapState.Published) {
        "$previewBaseUrl?id=${props.map.id}"
    } else if (version != null) {
        "$previewBaseUrl?url=${encodeURIComponent(version.downloadURL)}"
    } else {
        "#"
    }
    a(altLink) {
        attrs.title = "Preview"
        attrs.attributes["aria-label"] = "Preview"
        attrs.onClickFunction = {
            it.preventDefault()

            if (props.version?.state == EMapState.Published) {
                props.modal.current?.showById(props.map.id)
            } else {
                props.version?.downloadURL?.let { downloadURL ->
                    props.modal.current?.show(downloadURL)
                }
            }
        }
        i("fas fa-play text-info") {
            attrs.attributes["aria-hidden"] = "true"
        }
    }
    oneclick {
        mapId = props.map.id
        this.modal = modal
    }
    props.version?.let { v ->
        downloadZip {
            attrs.map = props.map
            attrs.version = v
        }
    }
}

external interface UploaderProps : RProps {
    var map: MapDetail
    var version: MapVersion?
}

val uploader = functionComponent<UploaderProps> { props ->
    routeLink("/profile/${props.map.uploader.id}") {
        +props.map.uploader.name
    }
    botInfo {
        attrs.version = props.version
        attrs.automapper = props.map.automapper
    }
    if (props.version?.state == EMapState.Published) {
        +" - "
        TimeAgo.default {
            attrs.date = props.map.uploaded.toString()
        }
    }
}

external interface ColoredCardProps : RProps {
    var color: String
    var icon: String?
    var title: String?
}

val coloredCard = functionComponent<ColoredCardProps> {
    div("card colored") {
        div("color ${it.color}") {
            if (it.title != null) {
                attrs.title = it.title ?: ""
            }

            if (it.icon != null) {
                i("fas ${it.icon} icon") {
                    attrs.attributes["aria-hidden"] = "true"
                }
            }
        }
        div("content") {
            it.children()
        }
    }
}

external interface MapTitleProps : RProps {
    var title: String
    var key: String
}

val mapTitle = functionComponent<MapTitleProps> {
    routeLink("/maps/${it.key}") {
        +it.title.ifBlank {
            "<NO NAME>"
        }
    }
}
