package io.beatmaps.playlist

import external.routeLink
import io.beatmaps.api.PlaylistFull
import io.beatmaps.common.SearchPlaylistConfig
import io.beatmaps.common.api.EPlaylistType
import io.beatmaps.common.api.MapAttr
import io.beatmaps.common.api.RankedFilter
import io.beatmaps.common.asQuery
import io.beatmaps.common.fixed
import io.beatmaps.common.formatTime
import io.beatmaps.common.human
import io.beatmaps.shared.coloredCard
import io.beatmaps.shared.itemUserInfo
import io.beatmaps.shared.map.rating
import io.beatmaps.user.ProfileTab
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.span
import react.fc
import web.cssom.ClassName

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

        div {
            attrs.className = ClassName("playlist-card" + if (props.small == true) "-small" else "")
            coloredCard {
                attrs.color = plAttrs.joinToString(" ") { it.color }
                attrs.title = plAttrs.joinToString(" + ") { it.name }
                attrs.classes = if (pl.type == EPlaylistType.System) "border-warning" else ""

                div {
                    attrs.className = ClassName("info")
                    img {
                        attrs.alt = "Cover Image"
                        attrs.src = pl.playlistImage
                        attrs.className = ClassName("cover")
                    }

                    div {
                        attrs.className = ClassName("title")
                        routeLink(pl.link()) {
                            +pl.name.ifEmpty {
                                "<NO NAME>"
                            }
                        }
                        itemUserInfo {
                            attrs.users = listOf(pl.owner)
                            attrs.tab = ProfileTab.PLAYLISTS
                            attrs.time = pl.createdAt
                        }
                    }
                }
                div {
                    attrs.className = ClassName("additional")
                    val plStats = pl.stats
                    if (pl.type == EPlaylistType.Search) {
                        div {
                            attrs.className = ClassName("rating")
                            i {
                                attrs.className = ClassName("fas fa-search me-1")
                            }
                        }
                        div {
                            attrs.className = ClassName("stats")
                            val config = pl.config
                            if (config is SearchPlaylistConfig) {
                                stats.filter { it.filter(config) }.take(5).forEach {
                                    div {
                                        val txt = it.text(config)
                                        if (it.icon.startsWith("/")) {
                                            img {
                                                attrs.alt = txt
                                                attrs.src = it.icon
                                                attrs.title = txt
                                                attrs.ariaLabel = txt
                                                attrs.width = 12.0
                                                attrs.height = 12.0
                                            }
                                        } else {
                                            i {
                                                attrs.className = ClassName("fas ${it.icon}")
                                                attrs.title = txt
                                                attrs.ariaLabel = txt
                                            }
                                        }
                                        it.value(config)?.let { v ->
                                            span {
                                                attrs.className = ClassName("ms-1")
                                                +v
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (plStats != null) {
                        div {
                            attrs.className = ClassName("rating")
                            rating {
                                attrs.up = plStats.upVotes
                                attrs.down = plStats.downVotes
                                attrs.rating = plStats.scoreOneDP
                            }
                        }
                        div {
                            attrs.className = ClassName("stats")
                            div {
                                i {
                                    attrs.className = ClassName("fas fa-map me-1")
                                    attrs.title = "Total amount of maps"
                                    attrs.ariaLabel = "Total amount of maps"
                                }
                                span {
                                    +plStats.totalMaps.toString()
                                }
                            }
                            div {
                                i {
                                    attrs.className = ClassName("fas fa-user me-1")
                                    attrs.title = "Total amount of mappers"
                                    attrs.ariaLabel = "Total amount of mappers"
                                }
                                span {
                                    +plStats.mapperCount.toString()
                                }
                            }
                            div {
                                i {
                                    attrs.className = ClassName("fas fa-clock me-1")
                                    attrs.title = "Total runtime of all maps combined"
                                    attrs.ariaLabel = "Total runtime of all maps combined"
                                }
                                span {
                                    +plStats.totalDuration.formatTime()
                                }
                            }
                            div {
                                img {
                                    attrs.alt = "NPS"
                                    attrs.src = "/static/icons/nps.png"
                                    attrs.className = ClassName("me-1")
                                    attrs.title = "NPS range"
                                    attrs.ariaLabel = "NPS range"
                                    attrs.width = 12.0
                                    attrs.height = 12.0
                                }
                                span {
                                    +"${plStats.minNpsTwoDP} - ${plStats.maxNpsTwoDP}"
                                }
                            }
                        }
                    }
                    div {
                        attrs.className = ClassName("buttons")
                        a {
                            attrs.href = pl.downloadURL
                            attrs.title = "Download"
                            attrs.ariaLabel = "Download"
                            i {
                                attrs.className = ClassName("fas fa-download text-info")
                            }
                        }
                        a {
                            attrs.href = pl.oneClickURL()
                            attrs.title = "One-Click"
                            attrs.ariaLabel = "One-Click"
                            i {
                                attrs.className = ClassName("fas fa-cloud-download-alt text-info")
                            }
                        }
                    }
                }
            }
        }
    } ?: run {
        div {
            attrs.className = ClassName("playlist-card loading")
        }
    }
}
