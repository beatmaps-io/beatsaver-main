package io.beatmaps.playlist

import io.beatmaps.api.PlaylistFull
import io.beatmaps.common.Config
import io.beatmaps.common.api.MapAttr
import io.beatmaps.common.formatTime
import io.beatmaps.shared.coloredCard
import io.beatmaps.shared.playlistOwner
import io.beatmaps.shared.rating
import kotlinx.browser.window
import kotlinx.html.title
import org.w3c.dom.HTMLDivElement
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
import react.createRef
import react.dom.a
import react.dom.div
import react.dom.i
import react.dom.img
import react.dom.span
import react.router.dom.routeLink
import react.setState

external interface PlaylistInfoProps : RProps {
    var playlist: PlaylistFull?
}

external interface PlaylistInfoState : RState {
    var loaded: Boolean?
    var height: String
}

class PlaylistInfo : RComponent<PlaylistInfoProps, PlaylistInfoState>() {
    private val divRef = createRef<HTMLDivElement>()

    override fun componentDidMount() {
        setState {
            height = ""
            loaded = false
        }
    }

    override fun componentDidUpdate(prevProps: PlaylistInfoProps, prevState: PlaylistInfoState, snapshot: Any) {
        if (state.loaded != true && props.playlist != null) {
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
        } else if (state.loaded == true && props.playlist == null) {
            setState {
                height = ""
                loaded = false
            }
        }
    }

    override fun RBuilder.render() {
        props.playlist?.let { pl ->
            val plAttrs = listOfNotNull(
                if (pl.curator != null) MapAttr.Curated else
                    if (pl.owner?.verifiedMapper == true) MapAttr.Verified else null
            )

            div("playlist-card") {
                coloredCard {
                    attrs.color = plAttrs.joinToString(" ") { it.color }
                    attrs.title = plAttrs.joinToString(" + ") { it.name }

                    div("info") {
                        img(src = pl.playlistImage, alt = "Cover Image", classes = "cover") { }

                        div("title") {
                            routeLink("/playlists/${pl.playlistId}") {
                                +pl.name.ifEmpty {
                                    "<NO NAME>"
                                }
                            }
                            playlistOwner {
                                attrs.owner = pl.owner
                                attrs.time = pl.createdAt
                            }
                        }
                    }
                    div("additional") {
                        if (pl.stats != null) {
                            div("rating") {
                                rating {
                                    attrs.up = pl.stats.upVotes
                                    attrs.down = pl.stats.downVotes
                                    attrs.rating = pl.stats.scoreOneDP
                                }
                            }
                            div("stats") {
                                div("me-4") {
                                    i("fas fa-map me-1") {
                                        attrs.title = "Total amount of maps"
                                        attrs.attributes["aria-label"] = "Total amount of maps"
                                    }
                                    span {
                                        +pl.stats.totalMaps.toString()
                                    }
                                }
                                div("me-4") {
                                    i("fas fa-user me-1") {
                                        attrs.title = "Total amount of mappers"
                                        attrs.attributes["aria-label"] = "Total amount of mappers"
                                    }
                                    span {
                                        +pl.stats.mapperCount.toString()
                                    }
                                }
                                div("me-4") {
                                    i("fas fa-clock me-1") {
                                        attrs.title = "Total runtime of all maps combined"
                                        attrs.attributes["aria-label"] = "Total runtime of all maps combined"
                                    }
                                    span {
                                        +pl.stats.totalDuration.formatTime()
                                    }
                                }
                                div {
                                    img("NPS", "/static/icons/nps.png", "me-1") {
                                        attrs.title = "NPS range"
                                        attrs.attributes["aria-label"] = "NPS range"
                                        attrs.width = "12"
                                        attrs.height = "12"
                                    }
                                    span {
                                        +"${pl.stats.minNpsTwoDP} - ${pl.stats.maxNpsTwoDP}"
                                    }
                                }
                            }
                        }
                        div("buttons") {
                            a("${Config.apiremotebase}/playlists/id/${pl.playlistId}/download") {
                                attrs.title = "Download"
                                attrs.attributes["aria-label"] = "Download"
                                i("fas fa-download text-info") { }
                            }
                            a("bsplaylist://playlist/${Config.apiremotebase}/playlists/id/${pl.playlistId}/download/beatsaver-${pl.playlistId}.bplist") {
                                attrs.title = "One-Click"
                                attrs.attributes["aria-label"] = "One-Click"
                                i("fas fa-cloud-download-alt text-info") { }
                            }
                        }
                    }
                }
            }
        } ?: run {
            div("playlist-card loading") { }
        }
    }
}

fun RBuilder.playlistInfo(handler: PlaylistInfoProps.() -> Unit): ReactElement {
    return child(PlaylistInfo::class) {
        this.attrs(handler)
    }
}
