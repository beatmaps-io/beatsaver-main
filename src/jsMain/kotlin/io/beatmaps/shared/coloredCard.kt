package io.beatmaps.shared

import kotlinx.html.DIV
import kotlinx.html.title
import react.PropsWithChildren
import react.dom.div
import react.dom.i
import react.fc

external interface ColoredCardProps : PropsWithChildren {
    var color: String
    var icon: String?
    var title: String?
    var extra: ((DIV) -> Unit)?
    var classes: String?
}

val coloredCard = fc<ColoredCardProps> {
    div("card colored " + (it.classes ?: "")) {
        it.extra?.invoke(attrs)

        div("color ${it.color}") {
            if (it.title != null) {
                attrs.title = it.title ?: ""
            }

            if (it.icon != null) {
                i("fas ${it.icon} icon") {
                    attrs.attributes["aria-hidden"] = "true"
                }
            }
        }
        div("content") {
            it.children()
        }
    }
}
