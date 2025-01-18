package io.beatmaps.maps.testplay

import io.beatmaps.util.fcmemo
import react.Props
import react.dom.html.HTMLAttributes
import react.dom.html.ReactHTML.article
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
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
    fun HTMLAttributes<*>.invoke()
}

val timelineEntry = fcmemo<TimelineEntryProps>("timelineEntry") { props ->
    article {
        className = ClassName("card border-${props.color ?: "primary"} ${props.className ?: ""}")
        props.id?.let { id ->
            this.id = id
        }
        div {
            className = ClassName("card-header icon bg-${props.color ?: "primary"}")
            i {
                className = ClassName("fas ${props.icon ?: "fa-comments"}")
            }
        }
        with(props.headerCallback) {
            this?.let {
                div {
                    className = ClassName("card-header ${props.headerClass ?: ""}")
                    invoke()
                }
            }
        }
        with(props.bodyCallback) {
            this?.let {
                div {
                    className = ClassName("card-body")
                    invoke()
                }
            }
        }
        with(props.footerCallback) {
            this?.let {
                div {
                    className = ClassName("card-footer")
                    invoke()
                }
            }
        }
    }
}
