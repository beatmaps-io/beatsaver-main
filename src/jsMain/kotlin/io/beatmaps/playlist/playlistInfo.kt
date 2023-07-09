package io.beatmaps.playlist

import external.routeLink
import io.beatmaps.api.PlaylistFull
import io.beatmaps.common.api.EPlaylistType
import io.beatmaps.common.api.MapAttr
import io.beatmaps.common.formatTime
import io.beatmaps.shared.coloredCard
import io.beatmaps.shared.playlistOwner
import io.beatmaps.shared.rating
import io.beatmaps.util.AutoSizeComponent
import io.beatmaps.util.AutoSizeComponentProps
import io.beatmaps.util.AutoSizeComponentState
import kotlinx.html.title
import react.RBuilder
import react.dom.a
import react.dom.div
import react.dom.i
import react.dom.img
import react.dom.span

external interface PlaylistInfoProps : AutoSizeComponentProps<PlaylistFull>
external interface PlaylistInfoState : AutoSizeComponentState

class PlaylistInfo : AutoSizeComponent<PlaylistFull, PlaylistInfoProps, PlaylistInfoState>(12) {
    override fun RBuilder.render() {
        props.obj?.let { pl ->
            val plAttrs = listOfNotNull(
                if (pl.curator != null) {
                    MapAttr.Curated
                } else if (pl.owner.verifiedMapper) {
                    MapAttr.Verified
                } else {
                    null
                }
            )

            div("playlist-card") {
                style(this)

                coloredCard {
                    attrs.color = plAttrs.joinToString(" ") { it.color }
                    attrs.title = plAttrs.joinToString(" + ") { it.name }
                    attrs.classes = if (pl.type == EPlaylistType.System) "border-warning" else ""

                    div("info") {
                        ref = divRef
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
                            a(pl.downloadURL) {
                                attrs.title = "Download"
                                attrs.attributes["aria-label"] = "Download"
                                i("fas fa-download text-info") { }
                            }
                            a("bsplaylist://playlist/${pl.downloadURL}/beatsaver-${pl.playlistId}.bplist") {
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

fun RBuilder.playlistInfo(handler: PlaylistInfoProps.() -> Unit) =
    child(PlaylistInfo::class) {
        this.attrs(handler)
    }
