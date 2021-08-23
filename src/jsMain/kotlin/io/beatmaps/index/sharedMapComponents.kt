package io.beatmaps.index
import external.TimeAgo
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapDifficulty
import io.beatmaps.api.MapVersion
import io.beatmaps.maps.diffImg
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import react.RProps
import react.RReadableRef
import react.dom.a
import react.dom.i
import react.dom.span
import react.functionComponent
import react.router.dom.routeLink
import kotlin.collections.set

external interface BotInfoProps : RProps {
    var version: MapVersion?
    var marginLeft: Boolean?
}

val botInfo = functionComponent<BotInfoProps> { props ->
    val score = (props.version?.sageScore ?: 0)
    val marginLeft = props.marginLeft ?: true
    if (score < -4) {
        span("badge badge-pill badge-danger " + if (marginLeft) "ml-2" else "mr-2") {
            attrs.title = "Made by a bot"
            +"Bot"
        }
    } else if (score < 0) {
        span("badge badge-pill badge-warning " + if (marginLeft) "ml-2" else "mr-2") {
            attrs.title = "Could be a bot"
            +"Unsure"
        }
    }
}

external interface DiffIconsProps : RProps {
    var diffs: List<MapDifficulty>?
}

val diffIcons = functionComponent<DiffIconsProps> { props ->
    props.diffs?.forEach { d ->
        span("badge badge-pill badge-${d.difficulty.color} mr-2 mb-1") {
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
    props.version?.let { v ->
        downloadZip {
            attrs.map = props.map
            attrs.version = v
        }
    }
    val altLink = props.version?.downloadURL?.let { "$previewBaseUrl?url=${encodeURIComponent(it)}" } ?: "#"
    a(altLink) {
        attrs.title = "Preview"
        attrs.attributes["aria-label"] = "Preview"
        attrs.onClickFunction = {
            it.preventDefault()
            props.version?.downloadURL?.let { downloadURL ->
                props.modal.current?.show(downloadURL)
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
    copyBsr {
        attrs.map = props.map
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
    }
    +" - "
    TimeAgo.default {
        attrs.date = props.map.uploaded.toString()
    }
}