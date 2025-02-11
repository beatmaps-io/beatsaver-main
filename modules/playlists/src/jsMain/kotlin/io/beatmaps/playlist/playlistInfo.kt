package io.beatmaps.playlist

import external.routeLink
import io.beatmaps.api.PlaylistFull
import io.beatmaps.common.SearchPlaylistConfig
import io.beatmaps.common.api.EPlaylistType
import io.beatmaps.common.api.MapAttr
import io.beatmaps.common.api.RankedFilter
import io.beatmaps.common.asQuery
import io.beatmaps.common.fixed
import io.beatmaps.common.human
import io.beatmaps.common.util.formatTime
import io.beatmaps.index.colorStr
import io.beatmaps.index.titleStr
import io.beatmaps.shared.coloredCard
import io.beatmaps.shared.itemUserInfo
import io.beatmaps.shared.map.rating
import io.beatmaps.user.ProfileTab
import io.beatmaps.util.fcmemo
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.span
import web.cssom.ClassName

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

fun PlaylistFull.attrs() = listOfNotNull(
    if (curator != null) {
        MapAttr.Curated
    } else if (owner.verifiedMapper) {
        MapAttr.Verified
    } else {
        null
    }
)

val playlistInfo = fcmemo<PlaylistInfoProps>("playlistInfo") { props ->
    props.playlist?.let { pl ->
        val plAttrs = pl.attrs()

        div {
            className = ClassName("playlist-card" + if (props.small == true) "-small" else "")
            coloredCard {
                color = plAttrs.colorStr()
                title = plAttrs.titleStr()
                classes = if (pl.type == EPlaylistType.System) "border-warning" else ""

                div {
                    className = ClassName("info")
                    img {
                        alt = "Cover Image"
                        src = pl.playlistImage
                        className = ClassName("cover")
                    }

                    div {
                        className = ClassName("title")
                        routeLink(pl.link()) {
                            +pl.name.ifEmpty {
                                "<NO NAME>"
                            }
                        }

                        itemUserInfo {
                            users = listOf(pl.owner)
                            tab = ProfileTab.PLAYLISTS
                            time = pl.createdAt
                        }
                    }
                }
                div {
                    className = ClassName("additional")
                    val plStats = pl.stats
                    if (pl.type == EPlaylistType.Search) {
                        div {
                            className = ClassName("rating")
                            i {
                                className = ClassName("fas fa-search me-1")
                            }
                        }
                        div {
                            className = ClassName("stats")
                            val config = pl.config
                            if (config is SearchPlaylistConfig) {
                                stats.filter { it.filter(config) }.take(5).forEach {
                                    div {
                                        val txt = it.text(config)
                                        if (it.icon.startsWith("/")) {
                                            img {
                                                alt = txt
                                                src = it.icon
                                                title = txt
                                                ariaLabel = txt
                                                width = 12.0
                                                height = 12.0
                                            }
                                        } else {
                                            i {
                                                className = ClassName("fas ${it.icon}")
                                                title = txt
                                                ariaLabel = txt
                                            }
                                        }
                                        it.value(config)?.let { v ->
                                            span {
                                                className = ClassName("ms-1")
                                                +v
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (plStats != null) {
                        div {
                            className = ClassName("rating")
                            rating {
                                up = plStats.upVotes
                                down = plStats.downVotes
                                rating = plStats.scoreOneDP
                            }
                        }
                        div {
                            className = ClassName("stats")
                            div {
                                i {
                                    className = ClassName("fas fa-map me-1")
                                    title = "Total amount of maps"
                                    ariaLabel = "Total amount of maps"
                                }
                                span {
                                    +plStats.totalMaps.toString()
                                }
                            }
                            div {
                                i {
                                    className = ClassName("fas fa-user me-1")
                                    title = "Total amount of mappers"
                                    ariaLabel = "Total amount of mappers"
                                }
                                span {
                                    +plStats.mapperCount.toString()
                                }
                            }
                            div {
                                i {
                                    className = ClassName("fas fa-clock me-1")
                                    title = "Total runtime of all maps combined"
                                    ariaLabel = "Total runtime of all maps combined"
                                }
                                span {
                                    +plStats.totalDuration.formatTime()
                                }
                            }
                            div {
                                img {
                                    alt = "NPS"
                                    src = "/static/icons/nps.png"
                                    className = ClassName("me-1")
                                    title = "NPS range"
                                    ariaLabel = "NPS range"
                                    width = 12.0
                                    height = 12.0
                                }
                                span {
                                    +"${plStats.minNpsTwoDP} - ${plStats.maxNpsTwoDP}"
                                }
                            }
                        }
                    }
                    div {
                        className = ClassName("buttons")
                        a {
                            href = pl.downloadURL
                            title = "Download"
                            ariaLabel = "Download"
                            i {
                                className = ClassName("fas fa-download text-info")
                            }
                        }
                        a {
                            href = pl.oneClickURL()
                            title = "One-Click"
                            ariaLabel = "One-Click"
                            i {
                                className = ClassName("fas fa-cloud-download-alt text-info")
                            }
                        }
                    }
                }
            }
        }
    } ?: run {
        div {
            className = ClassName("playlist-card loading")
        }
    }
}
