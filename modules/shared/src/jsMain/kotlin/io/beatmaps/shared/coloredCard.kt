package io.beatmaps.shared

import external.ClassName
import io.beatmaps.util.fcmemo
import react.PropsWithChildren
import react.dom.html.HTMLAttributes
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import web.html.HTMLDivElement

external interface ColoredCardProps : PropsWithChildren {
    var color: String
    var icon: String?
    var title: String?
    var extra: ((HTMLAttributes<HTMLDivElement>) -> Unit)?
    var classes: String?
}

val coloredCard = fcmemo<ColoredCardProps>("coloredCard") { props ->
    div {
        className = ClassName("card colored " + (props.classes ?: ""))
        props.extra?.invoke(this)

        div {
            className = ClassName("color ${props.color}")
            if (props.title != null) {
                title = props.title ?: ""
            }

            if (props.icon != null) {
                i {
                    className = ClassName("fas ${props.icon} icon")
                    ariaHidden = true
                }
            }
        }
        div {
            className = ClassName("content")
            +props.children
        }
    }
}
