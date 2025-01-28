package io.beatmaps.maps

import external.TimeAgo
import external.routeLink
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapDifficulty
import io.beatmaps.common.fixedStr
import io.beatmaps.common.util.formatTime
import io.beatmaps.shared.map.uploaderWithInfo
import io.beatmaps.shared.profileLink
import io.beatmaps.user.ProfileTab
import io.beatmaps.util.fcmemo
import react.ChildrenBuilder
import react.Props
import react.dom.aria.AriaRole
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.abbr
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.span
import web.cssom.ClassName

external interface InfoTableProps : Props {
    var map: MapDetail
    var horizontal: Boolean?
    var selected: MapDifficulty?
    var changeSelectedDiff: ((MapDifficulty) -> Unit)?
}

val infoTable = fcmemo<InfoTableProps>("infoTable") { props ->
    val itemClasses by lazy { ClassName("list-group-item d-flex justify-content-between" + if (props.horizontal == true) " col-lg" else "") }

    fun formatStat(value: Int) = when {
        value >= 1000000 -> "${(value / 10000) / 100f}M"
        value >= 100000 -> "${value / 1000}k"
        value >= 10000 -> "${(value / 100) / 10f}k"
        value >= 1000 -> "${(value / 10) / 100f}k"
        else -> value.toString()
    }

    fun ChildrenBuilder.mapItem(icon: String, info: String, value: String) =
        span {
            img {
                alt = info
                src = "/static/icons/$icon.png"
                className = ClassName("mode")
                title = info
                width = 16.0
                height = 16.0
            }
            +value
        }

    fun ChildrenBuilder.mapItem(icon: String, info: String, value: Int) = mapItem(icon, info, formatStat(value))

    fun ChildrenBuilder.mapItem(diff: MapDifficulty) =
        a {
            href = "#"
            className = ClassName("list-group-item d-flex stat-${diff.difficulty.color}" + (if (props.selected == diff) " active" else ""))
            role = AriaRole.button
            onClick = {
                it.preventDefault()
                props.changeSelectedDiff?.invoke(diff)
            }

            diffImg {
                this.diff = diff
            }

            +diff.difficulty.human()

            div {
                className = ClassName("stats")
                diff.stars?.let {
                    span {
                        className = ClassName("diff-stars" + if (diff.blStars == null) " rowspan-2" else "")
                        abbr {
                            title = "ScoreSaber"
                            +"SS"
                        }
                        +it.fixedStr(2)
                        i {
                            className = ClassName("fas fa-star")
                        }
                    }
                } ?: diff.blStars ?: mapItem("error", "Parity errors", diff.paritySummary.errors)

                mapItem("notes", "Notes", diff.notes)
                mapItem("bombs", "Bombs", diff.bombs)
                mapItem("walls", "Walls", diff.obstacles)

                diff.blStars?.let {
                    span {
                        className = ClassName("diff-stars" + if (diff.stars == null) " rowspan-2" else "")
                        abbr {
                            title = "BeatLeader"
                            +"BL"
                        }
                        +it.fixedStr(2)
                        i {
                            className = ClassName("fas fa-star")
                        }
                    }
                } ?: diff.stars ?: mapItem("warn", "Parity warnings", diff.paritySummary.warns)

                mapItem("njs", "Note jump speed", diff.njs.toString())
                mapItem("nps", "Notes per second", diff.nps.fixedStr(2))
                mapItem("lights", "Lights", diff.events)
            }
        }

    fun ChildrenBuilder.infoItem(label: String, info: String, href: String? = null) =
        if (info.isNotBlank()) {
            href?.let {
                routeLink(href, className = itemClasses) {
                    +label
                    span {
                        className = ClassName("text-truncate ms-4")
                        title = info
                        +info
                    }
                }
            } ?: div {
                className = itemClasses
                +label
                span {
                    className = ClassName("text-truncate ms-4")
                    +info
                }
            }
        } else { null }

    val publishedVersion = if (props.map.deletedAt == null) props.map.publishedVersion() else null

    div {
        className = ClassName("list-group" + if (props.horizontal == true) " list-group-horizontal row m-4" else "")
        div {
            className = itemClasses
            +(if (props.map.collaborators?.size != 0) "Mappers" else "Mapper")
            span {
                className = ClassName("ms-4 text-wrap text-end")
                uploaderWithInfo {
                    map = props.map
                    info = false
                }
                +" (${props.map.metadata.levelAuthorName})"
            }
        }
        if (props.map.declaredAi.markAsBot) {
            infoItem("AI", "Bot")
        }

        div {
            className = itemClasses
            +"Uploaded"
            props.map.uploaded?.let { uploadedAt ->
                TimeAgo.default {
                    date = uploadedAt.toString()
                }
            } ?: span {
                className = ClassName("text-truncate ms-4")
                +"Never published"
            }
        }

        props.map.curator?.let { curator ->
            infoItem("Curated by", curator.name, curator.profileLink(ProfileTab.CURATED))
        }

        if (props.map.tags.isNotEmpty()) {
            div {
                className = itemClasses
                +"Tags"
                span {
                    className = ClassName("text-truncate ms-4")
                    props.map.tags.forEach {
                        mapTag {
                            selected = true
                            margins = "ms-2"
                            tag = it
                        }
                    }
                }
            }
        }

        if (publishedVersion != null) {
            val envs = publishedVersion.diffs.groupBy { it.environment }.minus(null)
            if (envs.any()) {
                div {
                    className = itemClasses
                    +"Environment"
                    span {
                        className = ClassName("text-truncate ms-4")
                        envs.forEach { (env, diffs) ->
                            div {
                                className = ClassName("badge badge-${env?.color()} ms-2")
                                span {
                                    title = diffs.joinToString { "${it.difficulty.human()} ${it.characteristic.human()}" }
                                    +(env?.human() ?: "")
                                }
                            }
                        }
                    }
                }
            }

            if (publishedVersion.diffs.any { it.me || it.ne || it.chroma || it.cinema }) {
                div {
                    className = itemClasses
                    +"Mods"
                    span {
                        className = ClassName("text-truncate ms-4")
                        mapRequirements {
                            margins = "ms-2"
                            version = publishedVersion
                        }
                    }
                }
            }

            props.map.metadata.let { metadata ->
                infoItem("Song Length", metadata.duration.formatTime())
                infoItem("BPM", "${metadata.bpm}")
            }
        }

        props.map.stats.let { stats ->
            infoItem("Rating", "${stats.upvotes} / ${stats.downvotes} (${stats.scoreOneDP}%)")

            div {
                className = itemClasses
                +"Reviews"
                span {
                    className = ClassName("text-truncate ms-4")
                    span {
                        className = ClassName("text-" + stats.sentiment.color)
                        +stats.sentiment.human
                    }
                    +" (${stats.reviews} ${if (stats.reviews == 1) "review" else "reviews"})"
                }
            }
        }
    }

    div {
        className = ClassName("list-group mapstats")
        publishedVersion?.diffs?.groupBy { it.characteristic }?.forEach { char ->
            char.value.sortedByDescending { it.difficulty }.forEach { diff ->
                mapItem(diff)
            }
        }
    }
}
