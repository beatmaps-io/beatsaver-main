package io.beatmaps.index
import external.TimeAgo
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapDifficulty
import io.beatmaps.api.MapVersion
import io.beatmaps.common.Config
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import react.RBuilder
import react.RComponent
import react.RProps
import react.RReadableRef
import react.RState
import react.ReactElement
import react.dom.*
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
            img(d.characteristic.human(), "/static/icons/${d.characteristic.human().lowercase()}.png", classes = "mode") {
                attrs.title = d.characteristic.human()
                attrs.width = "16"
                attrs.height = "16"
            }
            +d.difficulty.human()
        }
    }
}

fun RDOMBuilder<*>.links(map: MapDetail, version: MapVersion?, modal: RReadableRef<ModalComponent>) {
    a("${Config.cdnbase}/${version?.hash}.zip", target = "_blank") {
        attrs.rel = "noopener"
        attrs.title = "Download zip"
        attrs.attributes["aria-label"] = "Download zip"
        i("fas fa-download text-info") {
            attrs.attributes["aria-hidden"] = "true"
        }
    }
    if (Config.oneClick && version?.key != null) {
        a("https://beatsaver.com/beatmap/${version?.key}", target = "_blank") {
            attrs.rel = "noopener"
            attrs.title = version?.key ?: ""
            attrs.attributes["aria-label"] = version?.key ?: ""
            i("fas fa-share-alt text-info") { }
        }
    }
    a("#") {
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
                small("text-center") {
                    +"${props.map.stats.upvotes} / ${props.map.stats.downvotes} (${(props.map.stats.score * 1000).toInt() / 10f}%)"
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