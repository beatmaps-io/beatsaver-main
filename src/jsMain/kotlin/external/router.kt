package external

import csstype.ClassName
import kotlinx.html.LABEL
import react.PropsWithChildren
import react.RBuilder
import react.RHandler
import react.router.dom.Link

fun ClassName(className: String?) = className?.let { ClassName(it) }

fun RBuilder.routeLink(href: String, className: String? = null, block: RHandler<PropsWithChildren>?) {
    Link {
        attrs.to = href
        attrs.replace = false
        attrs.className = ClassName(className)
        block?.invoke(this)
    }
}

var LABEL.reactFor: String
    get() = attributes["htmlFor"] ?: ""
    set(newValue) { attributes["htmlFor"] = newValue }
