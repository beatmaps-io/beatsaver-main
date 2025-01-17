package io.beatmaps.maps

import external.TimeAgo
import external.routeLink
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapDifficulty
import io.beatmaps.common.fixedStr
import io.beatmaps.common.formatTime
import io.beatmaps.shared.map.uploaderWithInfo
import io.beatmaps.shared.profileLink
import io.beatmaps.user.ProfileTab
import io.beatmaps.util.fcmemo
import react.Props
import react.RElementBuilder
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

    fun RElementBuilder<*>.mapItem(icon: String, info: String, value: String) =
        span {
            img {
                attrs.alt = info
                attrs.src = "/static/icons/$icon.png"
                attrs.className = ClassName("mode")
                attrs.title = info
                attrs.width = 16.0
                attrs.height = 16.0
            }
            +value
        }

    fun RElementBuilder<*>.mapItem(icon: String, info: String, value: Int) = mapItem(icon, info, formatStat(value))

    fun RElementBuilder<*>.mapItem(diff: MapDifficulty) =
        a {
            attrs.href = "#"
            attrs.className = ClassName("list-group-item d-flex stat-${diff.difficulty.color}" + (if (props.selected == diff) " active" else ""))
            attrs.role = AriaRole.button
            attrs.onClick = {
                it.preventDefault()
                props.changeSelectedDiff?.invoke(diff)
            }

            diffImg {
                attrs.diff = diff
            }

            +diff.difficulty.human()

            div {
                attrs.className = ClassName("stats")
                diff.stars?.let {
                    span {
                        attrs.className = ClassName("diff-stars" + if (diff.blStars == null) " rowspan-2" else "")
                        abbr {
                            attrs.title = "ScoreSaber"
                            +"SS"
                        }
                        +it.fixedStr(2)
                        i {
                            attrs.className = ClassName("fas fa-star")
                        }
                    }
                } ?: diff.blStars ?: mapItem("error", "Parity errors", diff.paritySummary.errors)

                mapItem("notes", "Notes", diff.notes)
                mapItem("bombs", "Bombs", diff.bombs)
                mapItem("walls", "Walls", diff.obstacles)

                diff.blStars?.let {
                    span {
                        attrs.className = ClassName("diff-stars" + if (diff.stars == null) " rowspan-2" else "")
                        abbr {
                            attrs.title = "BeatLeader"
                            +"BL"
                        }
                        +it.fixedStr(2)
                        i {
                            attrs.className = ClassName("fas fa-star")
                        }
                    }
                } ?: diff.stars ?: mapItem("warn", "Parity warnings", diff.paritySummary.warns)

                mapItem("njs", "Note jump speed", diff.njs.toString())
                mapItem("nps", "Notes per second", diff.nps.fixedStr(2))
                mapItem("lights", "Lights", diff.events)
            }
        }

    fun RElementBuilder<*>.infoItem(label: String, info: String, href: String? = null) =
        if (info.isNotBlank()) {
            href?.let {
                routeLink(href, className = itemClasses) {
                    +label
                    span {
                        attrs.className = ClassName("text-truncate ms-4")
                        attrs.title = info
                        +info
                    }
                }
            } ?: div {
                attrs.className = itemClasses
                +label
                span {
                    attrs.className = ClassName("text-truncate ms-4")
                    +info
                }
            }
        } else { null }

    val publishedVersion = if (props.map.deletedAt == null) props.map.publishedVersion() else null

    div {
        attrs.className = ClassName("list-group" + if (props.horizontal == true) " list-group-horizontal row m-4" else "")
        div {
            attrs.className = itemClasses
            +(if (props.map.collaborators?.size != 0) "Mappers" else "Mapper")
            span {
                attrs.className = ClassName("ms-4 text-wrap text-end")
                uploaderWithInfo {
                    attrs.map = props.map
                    attrs.info = false
                }
                +" (${props.map.metadata.levelAuthorName})"
            }
        }
        if (props.map.declaredAi.markAsBot) {
            infoItem("AI", "Bot")
        }

        div {
            attrs.className = itemClasses
            +"Uploaded"
            props.map.uploaded?.let { uploadedAt ->
                TimeAgo.default {
                    attrs.date = uploadedAt.toString()
                }
            } ?: span {
     attrs.className = ClassName("text-truncate ms-4")
                +"Never published"
            }
        }

        props.map.curator?.let { curator ->
            infoItem("Curated by", curator.name, curator.profileLink(ProfileTab.CURATED))
        }

        if (props.map.tags.isNotEmpty()) {
            div {
                attrs.className = itemClasses
                +"Tags"
                span {
                    attrs.className = ClassName("text-truncate ms-4")
                    props.map.tags.forEach {
                        mapTag {
                            attrs.selected = true
                            attrs.margins = "ms-2"
                            attrs.tag = it
                        }
                    }
                }
            }
        }

        if (publishedVersion != null) {
            val envs = publishedVersion.diffs.groupBy { it.environment }.minus(null)
            if (envs.any()) {
                div {
                    attrs.className = itemClasses
                    +"Environment"
                    span {
                        attrs.className = ClassName("text-truncate ms-4")
                        envs.forEach { (env, diffs) ->
                            div {
                                attrs.className = ClassName("badge badge-${env?.color()} ms-2")
                                span {
                                    attrs.title = diffs.joinToString { "${it.difficulty.human()} ${it.characteristic.human()}" }
                                    +(env?.human() ?: "")
                                }
                            }
                        }
                    }
                }
            }

            if (publishedVersion.diffs.any { it.me || it.ne || it.chroma || it.cinema }) {
                div {
                    attrs.className = itemClasses
                    +"Mods"
                    span {
                        attrs.className = ClassName("text-truncate ms-4")
                        mapRequirements {
                            attrs.margins = "ms-2"
                            attrs.version = publishedVersion
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
                attrs.className = itemClasses
                +"Reviews"
                span {
                    attrs.className = ClassName("text-truncate ms-4")
                    span {
                        attrs.className = ClassName("text-" + stats.sentiment.color)
                        +stats.sentiment.human
                    }
                    +" (${stats.reviews} ${if (stats.reviews == 1) "review" else "reviews"})"
                }
            }
        }
    }

    div {
        attrs.className = ClassName("list-group mapstats")
        publishedVersion?.diffs?.groupBy { it.characteristic }?.forEach { char ->
            char.value.sortedByDescending { it.difficulty }.forEach { diff ->
                mapItem(diff)
            }
        }
    }
}
