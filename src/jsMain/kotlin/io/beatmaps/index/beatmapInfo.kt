package io.beatmaps.index

import external.TimeAgo
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapVersion
import io.beatmaps.common.Config
import react.RBuilder
import react.RComponent
import react.RProps
import react.RReadableRef
import react.RState
import react.ReactElement
import react.dom.div
import react.dom.img
import react.dom.jsStyle
import react.dom.p
import react.dom.small
import react.router.dom.routeLink
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

external interface BeatmapInfoProps : RProps {
    var map: MapDetail
    var version: MapVersion?
    var modal: RReadableRef<ModalComponent>
}

@JsExport
class BeatmapInfo : RComponent<BeatmapInfoProps, RState>() {
    override fun RBuilder.render() {
        div("beatmap" + if (props.map.ranked) " ranked" else if (props.map.qualified) " qualified" else "") {
            div {
                val totalVotes = (props.map.stats.upvotes + props.map.stats.downvotes).toDouble()
                val rawScore = props.map.stats.upvotes / totalVotes
                val uncertainty = abs((rawScore - 0.5) * 2.0.pow(-log10(totalVotes + 1)))

                img(src = props.version?.coverURL, alt = "Cover Image", classes = "cover") {
                    attrs.width = "100"
                    attrs.height = "100"
                }
                small("text-center vote") {
                    div("u") {
                        attrs.jsStyle {
                            flex = props.map.stats.upvotes
                        }
                    }
                    div("o") {
                        attrs.jsStyle {
                            flex = if (totalVotes < 1) 1 else (uncertainty * totalVotes / (1 - uncertainty))
                        }
                    }
                    div("d") {
                        attrs.jsStyle {
                            flex = props.map.stats.downvotes
                        }
                    }
                }
                div("percentage") {
                    attrs.title = "${props.map.stats.upvotes}/${props.map.stats.downvotes}"
                    +"${(props.map.stats.score * 1000).toInt() / 10f}%"
                }
            }
            div("info") {
                routeLink("/maps/${props.map.id}") {
                    if (props.map.name.isNotBlank()) {
                        +props.map.name
                    } else {
                        +"<NO NAME>"
                    }
                }
                p {
                    uploader {
                        attrs.map = props.map
                        attrs.version = props.version
                    }
                }
                div("diffs") {
                    diffIcons {
                        attrs.diffs = props.version?.diffs
                    }
                }
            }
            div("links") {
                links {
                    attrs.map = props.map
                    attrs.version = props.version
                    attrs.modal = props.modal
                }
            }
        }
    }
}

fun RBuilder.beatmapInfo(handler: BeatmapInfoProps.() -> Unit): ReactElement {
    return child(BeatmapInfo::class) {
        this.attrs(handler)
    }
}
