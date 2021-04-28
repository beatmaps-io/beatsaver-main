package io.beatmaps.maps

import external.TimeAgo
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapDifficulty
import io.beatmaps.index.botInfo
import kotlinx.html.DIV
import kotlinx.html.js.onClickFunction
import kotlinx.html.role
import kotlinx.html.title
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
import react.dom.*
import react.router.dom.routeLink

external interface InfoTableProps : RProps {
    var map: MapDetail
    var selected: MapDifficulty?
    var changeSelectedDiff: (MapDifficulty) -> Unit
}

@JsExport
class InfoTable : RComponent<InfoTableProps, RState>() {
    override fun RBuilder.render() {
        div("col-lg-4 text-nowrap") {
            val publishedVersion = props.map.publishedVersion()
            div("list-group") {
                infoItem("Mapper", props.map.uploader.name, "/profile/${props.map.uploader.id}")

                div("list-group-item d-flex justify-content-between") {
                    +"Uploaded"
                    TimeAgo.default {
                        attrs.date = props.map.uploaded.toString()
                    }
                }

                div("list-group-item d-flex justify-content-between") {
                    +"Author"
                    span("text-truncate ml-4") {
                        botInfo(publishedVersion, false)
                        +props.map.metadata.levelAuthorName
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
    }

    private fun RDOMBuilder<*>.mapItem(diff: MapDifficulty) =
        a("#", classes = "list-group-item d-flex stat-${diff.difficulty.color}" + (if (props.selected == diff) " active" else "")) {
            attrs.role = "button"
            attrs.onClickFunction = {
                it.preventDefault()
                props.changeSelectedDiff(diff)
            }

            img(diff.characteristic.human(), "/static/icons/${diff.characteristic.human().toLowerCase()}.png", classes = "mode") {
                attrs.title = diff.characteristic.human()
                attrs.width = "16"
                attrs.height = "16"
            }

            +diff.difficulty.human()

            div("stats") {
                mapItem("error", "Parity errors", diff.paritySummary.errors)
                mapItem("notes", "Notes", diff.notes)
                mapItem("bombs", "Bombs", diff.bombs)
                mapItem("walls", "Walls", diff.obstacles)
                mapItem("warn", "Parity warnings", diff.paritySummary.warns)
                mapItem("njs", "Note jump speed", diff.njs.toString())
                mapItem("nps", "Notes per second", diff.nps.toString())
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
            img(info, "/static/icons/${icon}.png", classes = "mode") {
                attrs.title = info
                attrs.width = "16"
                attrs.height = "16"
            }
            +value
        }

    private fun RDOMBuilder<DIV>.infoItem(label: String, info: String, href: String? = null) =
        if (info.isNotBlank())
            href?.let {
                routeLink(href, className = "list-group-item d-flex justify-content-between") {
                    +label
                    span("text-truncate ml-4") {
                        +info
                    }
                }
            } ?: div("list-group-item d-flex justify-content-between") {
                +label
                span("text-truncate ml-4") {
                    +info
                }
            } else null
}

fun RBuilder.infoTable(handler: InfoTableProps.() -> Unit): ReactElement {
    return child(InfoTable::class) {
        this.attrs(handler)
    }
}