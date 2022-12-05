package external

import kotlinx.html.LABEL
import react.PropsWithChildren
import react.RBuilder
import react.RHandler
import react.router.dom.Link

fun RBuilder.routeLink(href: String, className: String? = null, block: RHandler<PropsWithChildren>?) {
    Link {
        attrs {
            this.to = href
            this.replace = false
            this.className = className
        }
        block?.invoke(this)
    }
}

var LABEL.reactFor: String
    get() = attributes["htmlFor"] ?: ""
    set(newValue) { attributes["htmlFor"] = newValue }
