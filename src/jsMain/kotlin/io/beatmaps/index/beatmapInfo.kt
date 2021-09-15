package io.beatmaps.index

import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapVersion
import io.beatmaps.common.formatTime
import kotlinx.html.title
import react.RBuilder
import react.RComponent
import react.RProps
import react.RReadableRef
import react.RState
import react.ReactElement
import react.dom.div
import react.dom.i
import react.dom.img
import react.dom.jsStyle
import react.dom.p
import react.dom.small
import react.dom.span
import react.router.dom.routeLink
import kotlin.math.abs
import kotlin.math.floor
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
            div("additional") {
                span {
                    +props.map.id
                    i("fas fa-key") {
                        attrs.attributes["aria-hidden"] = "true"
                    }
                }
                span {
                    +props.map.metadata.duration.formatTime()
                    i("fas fa-clock") {
                        attrs.attributes["aria-hidden"] = "true"
                    }
                }
                span {
                    +(floor(props.map.metadata.bpm * 100) / 100).toString()
                    img("Metronome", "/static/icons/metronome.svg") {
                        attrs.width = "12"
                        attrs.height = "12"
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
