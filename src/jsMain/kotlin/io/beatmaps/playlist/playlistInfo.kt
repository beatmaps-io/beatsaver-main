package io.beatmaps.playlist

import external.routeLink
import io.beatmaps.api.PlaylistFull
import io.beatmaps.common.SearchPlaylistConfig
import io.beatmaps.common.api.MapAttr
import io.beatmaps.common.api.RankedFilter
import io.beatmaps.common.asQuery
import io.beatmaps.common.fixed
import io.beatmaps.common.formatTime
import io.beatmaps.common.human
import io.beatmaps.shared.coloredCard
import kotlinx.html.title
import react.Props
import react.dom.a
import react.dom.div
import react.dom.i
import react.dom.img
import react.dom.span
import react.fc

external interface PlaylistInfoProps : Props {
    var playlist: PlaylistFull?
    var small: Boolean?
}

data class StatInfo(val icon: String, val text: (SearchPlaylistConfig) -> String, val filter: (SearchPlaylistConfig) -> Boolean = { true }, val value: (SearchPlaylistConfig) -> String? = { null })

val stats = listOf(
    StatInfo("fa-search", { it.searchParams.search }, { it.searchParams.search.isNotEmpty() }),
    StatInfo("fa-tag", { it.searchParams.tags.asQuery().human() }, { it.searchParams.tags.isNotEmpty() }),

    StatInfo("/static/icons/nps.png", { "NPS Range" }, { it.searchParams.minNps != null || it.searchParams.maxNps != null }) {
        "${(it.searchParams.minNps ?: 0f).fixed(2)} - ${it.searchParams.maxNps?.fixed(2) ?: "∞"}"
    },

    StatInfo("fa-award", { "Curated" }, { it.searchParams.curated == true }),
    StatInfo("fa-star", { "Ranked" }, { it.searchParams.ranked != RankedFilter.All }),
    StatInfo("fa-certificate", { "Verified" }, { it.searchParams.verified == true }),
    StatInfo("fa-robot", { "AI Included" }, { it.searchParams.automapper == true }),

    StatInfo("fa-hat-wizard", { "Noodle" }, { it.searchParams.noodle == true }),
    StatInfo("fa-hat-wizard", { "Chroma" }, { it.searchParams.chroma == true }),
    StatInfo("fa-video", { "Cinema" }, { it.searchParams.cinema == true }),
    StatInfo("fa-hat-wizard", { "Mapping Extensions" }, { it.searchParams.me == true }),

    StatInfo("fa-map", { "Maximum maps" }, { true }) { it.mapCount.toString() },
    StatInfo("fa-sort-amount-down", { it.searchParams.sortOrder.name }, { true })
)

val playlistInfo = fc<PlaylistInfoProps>("playlistInfo") { props ->
    props.playlist?.let { pl ->
        val plAttrs = listOfNotNull(
            if (pl.curator != null) {
                MapAttr.Curated
            } else if (pl.owner.verifiedMapper) {
                MapAttr.Verified
            } else {
                null
            }
        )

        div("playlist-card" + if (props.small == true) "-small" else "") {
            coloredCard {
                attrs.color = plAttrs.joinToString(" ") { it.color }
                attrs.title = plAttrs.joinToString(" + ") { it.name }
                attrs.classes = if (pl.type == io.beatmaps.common.api.EPlaylistType.System) "border-warning" else ""

                div("info") {
                    img(src = pl.playlistImage, alt = "Cover Image", classes = "cover") { }

                    div("title") {
                        routeLink(pl.link()) {
                            +pl.name.ifEmpty {
                                "<NO NAME>"
                            }
                        }
                        io.beatmaps.shared.playlistOwner {
                            attrs.owner = pl.owner
                            attrs.time = pl.createdAt
                        }
                    }
                }
                div("additional") {
                    if (pl.type == io.beatmaps.common.api.EPlaylistType.Search) {
                        div("rating") {
                            i("fas fa-search me-1") { }
                        }
                        div("stats") {
                            if (pl.config is SearchPlaylistConfig) {
                                stats.filter { it.filter(pl.config) }.take(5).forEach {
                                    div {
                                        val txt = it.text(pl.config)
                                        if (it.icon.startsWith("/")) {
                                            img(txt, it.icon) {
                                                attrs.title = txt
                                                attrs.attributes["aria-label"] = txt
                                                attrs.width = "12"
                                                attrs.height = "12"
                                            }
                                        } else {
                                            i("fas ${it.icon}") {
                                                attrs.title = txt
                                                attrs.attributes["aria-label"] = txt
                                            }
                                        }
                                        it.value(pl.config)?.let { v ->
                                            span("ms-1") {
                                                +v
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (pl.stats != null) {
                        div("rating") {
                            io.beatmaps.shared.map.rating {
                                attrs.up = pl.stats.upVotes
                                attrs.down = pl.stats.downVotes
                                attrs.rating = pl.stats.scoreOneDP
                            }
                        }
                        div("stats") {
                            div {
                                i("fas fa-map me-1") {
                                    attrs.title = "Total amount of maps"
                                    attrs.attributes["aria-label"] = "Total amount of maps"
                                }
                                span {
                                    +pl.stats.totalMaps.toString()
                                }
                            }
                            div {
                                i("fas fa-user me-1") {
                                    attrs.title = "Total amount of mappers"
                                    attrs.attributes["aria-label"] = "Total amount of mappers"
                                }
                                span {
                                    +pl.stats.mapperCount.toString()
                                }
                            }
                            div {
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
                        a(pl.oneClickURL()) {
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
