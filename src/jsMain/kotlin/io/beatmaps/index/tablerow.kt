package io.beatmaps.index
import external.TimeAgo
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapDifficulty
import io.beatmaps.api.MapVersion
import io.beatmaps.common.Config
import io.beatmaps.maps.diffImg
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import react.RBuilder
import react.RComponent
import react.RProps
import react.RReadableRef
import react.RState
import react.ReactElement
import react.dom.RDOMBuilder
import react.dom.a
import react.dom.i
import react.dom.img
import react.dom.p
import react.dom.small
import react.dom.span
import react.dom.td
import react.dom.tr
import react.router.dom.routeLink
import kotlin.collections.set

external interface TableRowProps : RProps {
    var map: MapDetail
    var version: MapVersion?
    var modal: RReadableRef<ModalComponent>
}

fun RDOMBuilder<*>.botInfo(version: MapVersion?, marginLeft: Boolean = true) {
    if (version?.sageScore ?: 0 < -4) {
        span("badge badge-pill badge-danger " + if (marginLeft) "ml-2" else "mr-2") {
            attrs.title = "Made by a bot"
            +"Bot"
        }
    } else if (version?.sageScore ?: 0 < 0) {
        span("badge badge-pill badge-warning " + if (marginLeft) "ml-2" else "mr-2") {
            attrs.title = "Could be a bot"
            +"Unsure"
        }
    }
}

fun RDOMBuilder<*>.diffIcons(diffs: List<MapDifficulty>?) {
    diffs?.forEach { d ->
        span("badge badge-pill badge-${d.difficulty.color} mr-2 mb-1") {
            diffImg(d)
            +d.difficulty.human()
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

fun RDOMBuilder<*>.links(map: MapDetail, version: MapVersion?, modal: RReadableRef<ModalComponent>) {
    a("${Config.cdnbase}/beatsaver/${map.id}.zip", target = "_blank") {
        attrs.rel = "noopener"
        attrs.title = "Download zip"
        attrs.attributes["aria-label"] = "Download zip"
        i("fas fa-download text-info") {
            attrs.attributes["aria-hidden"] = "true"
        }
    }
    val altLink = version?.downloadURL?.let { "$previewBaseUrl?url=${encodeURIComponent(it)}" } ?: "#"
    a(altLink) {
        attrs.title = "Preview"
        attrs.attributes["aria-label"] = "Preview"
        attrs.onClickFunction = {
            it.preventDefault()
            version?.hash?.let { hash ->
                modal.current?.show(hash)
            }
        }
        i("fas fa-play text-info") {
            attrs.attributes["aria-hidden"] = "true"
        }
    }
    oneclick {
        mapId = map.id
        this.modal = modal
    }
    a("#") {
        attrs.title = "Copy BSR"
        attrs.attributes["aria-label"] = "Copy BSR"
        attrs.onClickFunction = {
            it.preventDefault()
            setClipboard("!bsr ${map.id}")
        }
        i("fab fa-twitch text-info") {
            attrs.attributes["aria-hidden"] = "true"
        }
    }
}

@JsExport
class TableRow : RComponent<TableRowProps, RState>() {
    override fun RBuilder.render() {
        tr {
            td {
                img(src = "${Config.cdnbase}/${props.version?.hash}.jpg", alt = "Cover Image", classes = "cover") {
                    attrs.width = "100"
                    attrs.height = "100"
                }
            }
            td {
                routeLink("/maps/${props.map.id}") {
                    +props.map.name
                }
                p {
                    routeLink("/profile/${props.map.uploader.id}") {
                        +props.map.uploader.name
                    }
                    botInfo(props.version)
                    +" - "
                    TimeAgo.default {
                        attrs.date = props.map.uploaded.toString()
                    }
                    small {
                        +props.map.description.replace("\n", " ")
                    }
                }
            }
            td("diffs") {
                diffIcons(props.version?.diffs)
            }
            td("links") {
                links(props.map, props.version, props.modal)
            }
        }
    }
}

fun RBuilder.beatmapTableRow(handler: TableRowProps.() -> Unit): ReactElement {
    return child(TableRow::class) {
        this.attrs(handler)
    }
}
