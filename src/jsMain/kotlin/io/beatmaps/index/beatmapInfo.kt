package io.beatmaps.index

import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapVersion
import io.beatmaps.common.api.MapAttr
import io.beatmaps.common.formatTime
import io.beatmaps.globalContext
import io.beatmaps.playlist.addToPlaylist
import io.beatmaps.shared.coloredCard
import io.beatmaps.shared.diffIcons
import io.beatmaps.shared.links
import io.beatmaps.shared.mapTitle
import io.beatmaps.shared.rating
import io.beatmaps.shared.uploader
import kotlinx.browser.window
import org.w3c.dom.HTMLDivElement
import react.RBuilder
import react.RComponent
import react.RProps
import react.RReadableRef
import react.RState
import react.ReactElement
import react.createRef
import react.dom.div
import react.dom.i
import react.dom.img
import react.dom.jsStyle
import react.dom.p
import react.dom.span
import react.setState
import kotlin.math.floor

external interface BeatmapInfoProps : RProps {
    var map: MapDetail?
    var version: MapVersion?
    var modal: RReadableRef<ModalComponent>
}

external interface BeatMapInfoState : RState {
    var loaded: Boolean?
    var height: String
}

class BeatmapInfo : RComponent<BeatmapInfoProps, BeatMapInfoState>() {
    private val divRef = createRef<HTMLDivElement>()

    override fun componentDidMount() {
        setState {
            height = ""
            loaded = false
        }
    }

    override fun componentDidUpdate(prevProps: BeatmapInfoProps, prevState: BeatMapInfoState, snapshot: Any) {
        if (state.loaded != true && props.map != null) {
            val innerSize = divRef.current?.scrollHeight?.let { it + 30 } ?: 0
            setState {
                loaded = true
                height = "${innerSize}px"
            }

            window.setTimeout({
                setState {
                    height = "auto"
                }
            }, 200)
        } else if (state.loaded == true && props.map == null) {
            setState {
                height = ""
                loaded = false
            }
        }
    }

    override fun RBuilder.render() {
        props.map?.let { map ->
            val mapAttrs = listOfNotNull(
                if (map.ranked) MapAttr.Ranked else null,
                if (map.qualified && !map.ranked) MapAttr.Qualified else null,
                if (map.curator != null) MapAttr.Curated else null
            ).ifEmpty {
                listOfNotNull(
                    if (map.uploader.verifiedMapper) MapAttr.Verified else null
                )
            }

            div("beatmap") {
                attrs.jsStyle {
                    height = state.height
                }

                coloredCard {
                    attrs.color = mapAttrs.joinToString(" ") { it.color }
                    attrs.title = mapAttrs.joinToString(" + ") { it.name }

                    div {
                        img(src = props.version?.coverURL, alt = "Cover Image", classes = "cover") {
                            attrs.width = "100"
                            attrs.height = "100"
                        }
                        rating {
                            attrs.up = map.stats.upvotes
                            attrs.down = map.stats.downvotes
                            attrs.rating = map.stats.scoreOneDP
                        }
                    }
                    div("info") {
                        ref = divRef

                        mapTitle {
                            attrs.title = map.name
                            attrs.mapKey = map.id
                        }
                        p {
                            uploader {
                                attrs.map = map
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
                            +map.id
                            i("fas fa-key") {
                                attrs.attributes["aria-hidden"] = "true"
                            }
                        }
                        span {
                            +map.metadata.duration.formatTime()
                            i("fas fa-clock") {
                                attrs.attributes["aria-hidden"] = "true"
                            }
                        }
                        span {
                            +(floor(map.metadata.bpm * 100) / 100).toString()
                            img("Metronome", "/static/icons/metronome.svg") {
                                attrs.width = "12"
                                attrs.height = "12"
                            }
                        }
                        globalContext.Consumer { userData ->
                            if (userData != null) {
                                addToPlaylist {
                                    this.map = map
                                    modal = props.modal
                                }
                            }
                        }
                    }
                    div("links") {
                        links {
                            attrs.map = map
                            attrs.version = props.version
                            attrs.modal = props.modal
                        }
                    }
                }
            }
        } ?: run {
            div("beatmap loading") { }
        }
    }
}

fun RBuilder.beatmapInfo(handler: BeatmapInfoProps.() -> Unit): ReactElement {
    return child(BeatmapInfo::class) {
        this.attrs(handler)
    }
}
