package io.beatmaps.shared

import external.ClassName
import react.PropsWithChildren
import react.dom.html.HTMLAttributes
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.fc
import web.html.HTMLDivElement

external interface ColoredCardProps : PropsWithChildren {
    var color: String
    var icon: String?
    var title: String?
    var extra: ((HTMLAttributes<HTMLDivElement>) -> Unit)?
    var classes: String?
}

val coloredCard = fc<ColoredCardProps>("coloredCard") {
    div {
        attrs.className = ClassName("card colored " + (it.classes ?: ""))
        it.extra?.invoke(attrs)

        div {
            attrs.className = ClassName("color ${it.color}")
            if (it.title != null) {
                attrs.title = it.title ?: ""
            }

            if (it.icon != null) {
                i {
                    attrs.className = ClassName("fas ${it.icon} icon")
                    attrs.ariaHidden = true
                }
            }
        }
        div {
            attrs.className = ClassName("content")
            it.children()
        }
    }
}
