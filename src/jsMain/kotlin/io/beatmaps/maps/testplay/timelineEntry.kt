package io.beatmaps.maps.testplay

import kotlinx.html.DIV
import kotlinx.html.id
import react.Props
import react.dom.RDOMBuilder
import react.dom.article
import react.dom.div
import react.dom.i
import react.fc

external interface TimelineEntryProps : Props {
    var headerCallback: TimelineEntrySectionRenderer?
    var bodyCallback: TimelineEntrySectionRenderer?
    var footerCallback: TimelineEntrySectionRenderer?
    var icon: String?
    var color: String?
    var id: String?
    var className: String?
}

fun interface TimelineEntrySectionRenderer {
    fun RDOMBuilder<DIV>.invoke()
}

val timelineEntry = fc<TimelineEntryProps>("timelineEntry") { props ->
    article("card border-${props.color ?: "primary"} ${props.className ?: ""}") {
        props.id?.let { id ->
            attrs.id = id
        }
        div("card-header icon bg-${props.color ?: "primary"}") {
            i("fas ${props.icon ?: "fa-comments"}") {}
        }
        with(props.headerCallback) {
            this?.let {
                div("card-header d-flex") {
                    invoke()
                }
            }
        }
        with(props.bodyCallback) {
            this?.let {
                div("card-body") {
                    invoke()
                }
            }
        }
        with(props.footerCallback) {
            this?.let {
                div("card-footer") {
                    invoke()
                }
            }
        }
    }
}
