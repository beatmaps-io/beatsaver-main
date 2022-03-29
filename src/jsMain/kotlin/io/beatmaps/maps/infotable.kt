package io.beatmaps.maps

import external.TimeAgo
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapDifficulty
import io.beatmaps.common.formatTime
import kotlinx.html.DIV
import kotlinx.html.js.onClickFunction
import kotlinx.html.role
import kotlinx.html.title
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
import react.dom.RDOMBuilder
import react.dom.a
import react.dom.div
import react.dom.i
import react.dom.img
import react.dom.span
import react.router.dom.routeLink
import kotlin.math.floor

external interface InfoTableProps : RProps {
    var map: MapDetail
    var horizontal: Boolean?
    var selected: MapDifficulty?
    var changeSelectedDiff: (MapDifficulty) -> Unit
}

fun RDOMBuilder<*>.diffImg(diff: MapDifficulty) {
    val humanText = diff.characteristic.human()

    img(humanText, "/static/icons/${humanText.lowercase()}.svg", classes = "mode") {
        attrs.title = diff.difficulty.human() + " " + diff.characteristic.human()
        attrs.width = "16"
        attrs.height = "16"
    }
}

class InfoTable : RComponent<InfoTableProps, RState>() {
    private val itemClasses by lazy { "list-group-item d-flex justify-content-between" + if (props.horizontal == true) " col-lg" else "" }

    override fun RBuilder.render() {
        val publishedVersion = if (props.map.deletedAt == null) props.map.publishedVersion() else null
        val mapAttributes = listOfNotNull(
            if (props.map.ranked) "ranked" else null,
            if (props.map.qualified && !props.map.ranked) "qualified" else null,
            if (props.map.curator != null) "curated" else null,
            if (!props.map.ranked && !props.map.qualified && props.map.curator == null && props.map.uploader.verifiedMapper) "verified" else null
        )

        val classes = mapAttributes.plus("list-group").joinToString(" ")

        div(classes + if (props.horizontal == true) " list-group-horizontal row m-4" else "") {
            div("color") {
                attrs.title = mapAttributes.joinToString(" + ")
            }

            infoItem("Mapper", "${props.map.uploader.name} (${props.map.metadata.levelAuthorName})", "/profile/${props.map.uploader.id}")
            val score = publishedVersion?.sageScore ?: 0
            if (score < -4 || props.map.automapper) {
                infoItem("AI", if (score < -4) "Bot" else "Unsure")
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
                infoItem("Curated by", curator.name, "/profile/${curator.id}#curated")
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

            props.map.metadata.let { metadata ->
                infoItem("Song Length", metadata.duration.formatTime())
                infoItem("BPM", "${metadata.bpm}")
            }

            props.map.stats.let { stats ->
                infoItem("Rating", "${stats.upvotes} / ${stats.downvotes} (${stats.scoreOneDP}%)")
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

    private fun RDOMBuilder<*>.mapItem(diff: MapDifficulty) =
        a("#", classes = "list-group-item d-flex stat-${diff.difficulty.color}" + (if (props.selected == diff) " active" else "")) {
            attrs.role = "button"
            attrs.onClickFunction = {
                it.preventDefault()
                props.changeSelectedDiff(diff)
            }

            diffImg(diff)

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
                mapItem("nps", "Notes per second", (floor(diff.nps * 100) / 100).toString())
                mapItem("lights", "Lights", diff.events)
            }
        }

    private fun formatStat(value: Int) = when {
        value >= 1000000 -> "${(value / 10000) / 100f}M"
        value >= 100000 -> "${value / 1000}k"
        value >= 10000 -> "${(value / 100) / 10f}k"
        value >= 1000 -> "${(value / 10) / 100f}k"
        else -> value.toString()
    }
    private fun RDOMBuilder<*>.mapItem(icon: String, info: String, value: Int) = mapItem(icon, info, formatStat(value))

    private fun RDOMBuilder<*>.mapItem(icon: String, info: String, value: String) =
        span {
            img(info, "/static/icons/$icon.png", classes = "mode") {
                attrs.title = info
                attrs.width = "16"
                attrs.height = "16"
            }
            +value
        }

    private fun RDOMBuilder<DIV>.infoItem(label: String, info: String, href: String? = null) =
        if (info.isNotBlank())
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
            } else null
}

fun RBuilder.infoTable(handler: InfoTableProps.() -> Unit): ReactElement {
    return child(InfoTable::class) {
        this.attrs(handler)
    }
}
