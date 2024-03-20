package io.beatmaps.maps

import external.TimeAgo
import external.routeLink
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapDifficulty
import io.beatmaps.api.ReviewConstants
import io.beatmaps.common.fixedStr
import io.beatmaps.common.formatTime
import io.beatmaps.shared.map.uploader
import kotlinx.html.DIV
import kotlinx.html.js.onClickFunction
import kotlinx.html.role
import kotlinx.html.title
import react.Props
import react.dom.RDOMBuilder
import react.dom.a
import react.dom.div
import react.dom.i
import react.dom.img
import react.dom.span
import react.fc

external interface InfoTableProps : Props {
    var map: MapDetail
    var horizontal: Boolean?
    var selected: MapDifficulty?
    var changeSelectedDiff: ((MapDifficulty) -> Unit)?
}

val infoTable = fc<InfoTableProps> { props ->
    val itemClasses by lazy { "list-group-item d-flex justify-content-between" + if (props.horizontal == true) " col-lg" else "" }

    fun formatStat(value: Int) = when {
        value >= 1000000 -> "${(value / 10000) / 100f}M"
        value >= 100000 -> "${value / 1000}k"
        value >= 10000 -> "${(value / 100) / 10f}k"
        value >= 1000 -> "${(value / 10) / 100f}k"
        else -> value.toString()
    }

    fun RDOMBuilder<*>.mapItem(icon: String, info: String, value: String) =
        span {
            img(info, "/static/icons/$icon.png", classes = "mode") {
                attrs.title = info
                attrs.width = "16"
                attrs.height = "16"
            }
            +value
        }

    fun RDOMBuilder<*>.mapItem(icon: String, info: String, value: Int) = mapItem(icon, info, formatStat(value))

    fun RDOMBuilder<*>.mapItem(diff: MapDifficulty) =
        a("#", classes = "list-group-item d-flex stat-${diff.difficulty.color}" + (if (props.selected == diff) " active" else "")) {
            attrs.role = "button"
            attrs.onClickFunction = {
                it.preventDefault()
                props.changeSelectedDiff?.invoke(diff)
            }

            diffImg {
                attrs.diff = diff
            }

            +diff.difficulty.human()

            div("stats") {
                diff.stars?.let {
                    span("diff-stars") {
                        i("fas fa-star") {}
                        +it.toString()
                    }
                } ?: mapItem("error", "Parity errors", diff.paritySummary.errors)

                mapItem("notes", "Notes", diff.notes)
                mapItem("bombs", "Bombs", diff.bombs)
                mapItem("walls", "Walls", diff.obstacles)
                diff.stars ?: mapItem("warn", "Parity warnings", diff.paritySummary.warns)
                mapItem("njs", "Note jump speed", diff.njs.toString())
                mapItem("nps", "Notes per second", diff.nps.fixedStr(2))
                mapItem("lights", "Lights", diff.events)
            }
        }

    fun RDOMBuilder<DIV>.infoItem(label: String, info: String, href: String? = null) =
        if (info.isNotBlank()) {
            href?.let {
                routeLink(href, className = itemClasses) {
                    +label
                    span("text-truncate ms-4") {
                        attrs.title = info
                        +info
                    }
                }
            } ?: div(itemClasses) {
                +label
                span("text-truncate ms-4") {
                    +info
                }
            }
        } else { null }

    val publishedVersion = if (props.map.deletedAt == null) props.map.publishedVersion() else null

    div("list-group" + if (props.horizontal == true) " list-group-horizontal row m-4" else "") {
        div(itemClasses) {
            +(if (props.map.collaborators?.size != 0) "Mappers" else "Mapper")
            span("ms-4 text-wrap") {
                uploader {
                    attrs.map = props.map
                }
                +" (${props.map.metadata.levelAuthorName})"
            }
        }
        if (props.map.declaredAi.markAsBot) {
            infoItem("AI", "Bot")
        }

        div(itemClasses) {
            +"Uploaded"
            props.map.uploaded?.let { uploadedAt ->
                TimeAgo.default {
                    attrs.date = uploadedAt.toString()
                }
            } ?: span("text-truncate ms-4") {
                +"Never published"
            }
        }

        props.map.curator?.let { curator ->
            infoItem("Curated by", curator.name, curator.profileLink("curated"))
        }

        if (props.map.tags.isNotEmpty()) {
            div(itemClasses) {
                +"Tags"
                span("text-truncate ms-4") {
                    props.map.tags.forEach {
                        mapTag {
                            attrs.selected = true
                            attrs.margins = "ms-2"
                            attrs.tag = it
                            attrs.onClick = { }
                        }
                    }
                }
            }
        }

        if (publishedVersion != null) {
            if (publishedVersion.diffs.any { it.me || it.ne || it.chroma || it.cinema }) {
                div(itemClasses) {
                    +"Mods"
                    span("text-truncate ms-4") {
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

            if (ReviewConstants.COMMENTS_ENABLED) {
                div(itemClasses) {
                    +"Reviews"
                    span("text-truncate ms-4") {
                        span("text-" + stats.sentiment.color) {
                            +stats.sentiment.human
                        }
                        +" (${stats.reviews} ${if (stats.reviews == 1) "review" else "reviews"})"
                    }
                }
            }
        }
    }

    div("list-group mapstats") {
        publishedVersion?.diffs?.groupBy { it.characteristic }?.forEach { char ->
            char.value.sortedByDescending { it.difficulty }.forEach { diff ->
                mapItem(diff)
            }
        }
    }
}
