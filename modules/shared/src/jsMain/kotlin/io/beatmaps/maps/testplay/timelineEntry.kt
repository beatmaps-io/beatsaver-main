package io.beatmaps.maps.testplay

import react.Props
import react.RElementBuilder
import react.dom.html.HTMLAttributes
import react.dom.html.ReactHTML.article
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.fc
import web.cssom.ClassName

external interface TimelineEntryProps : Props {
    var headerCallback: TimelineEntrySectionRenderer?
    var bodyCallback: TimelineEntrySectionRenderer?
    var footerCallback: TimelineEntrySectionRenderer?
    var icon: String?
    var color: String?
    var id: String?
    var className: String?
    var headerClass: String?
}

fun interface TimelineEntrySectionRenderer {
    fun RElementBuilder<HTMLAttributes<*>>.invoke()
}

val timelineEntry = fc<TimelineEntryProps>("timelineEntry") { props ->
    article {
        attrs.className = ClassName("card border-${props.color ?: "primary"} ${props.className ?: ""}")
        props.id?.let { id ->
            attrs.id = id
        }
        div {
            attrs.className = ClassName("card-header icon bg-${props.color ?: "primary"}")
            i {
                attrs.className = ClassName("fas ${props.icon ?: "fa-comments"}")
            }
        }
        with(props.headerCallback) {
            this?.let {
                div {
                    attrs.className = ClassName("card-header ${props.headerClass ?: ""}")
                    invoke()
                }
            }
        }
        with(props.bodyCallback) {
            this?.let {
                div {
                    attrs.className = ClassName("card-body")
                    invoke()
                }
            }
        }
        with(props.footerCallback) {
            this?.let {
                div {
                    attrs.className = ClassName("card-footer")
                    invoke()
                }
            }
        }
    }
}
