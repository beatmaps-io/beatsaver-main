package external

import react.PropsWithChildren
import react.RBuilder
import react.RHandler
import react.router.dom.Link
import web.cssom.ClassName
import web.window.WindowTarget

fun ClassName(className: String?) = className?.let { ClassName(it) }

fun RBuilder.routeLink(href: String, className: String, id: String? = null, target: WindowTarget? = null, block: RHandler<PropsWithChildren>?) =
    routeLink(href, ClassName(className), id, target, block)

fun RBuilder.routeLink(href: String, className: ClassName? = null, id: String? = null, target: WindowTarget? = null, block: RHandler<PropsWithChildren>?) {
    Link {
        attrs.id = id
        attrs.to = href
        attrs.replace = false
        attrs.className = className
        attrs.target = target
        block?.invoke(this)
    }
}
