package external

import react.ChildrenBuilder
import react.router.dom.Link
import web.cssom.ClassName
import web.window.WindowTarget

fun ClassName(className: String?) = className?.let { ClassName(it) }

fun ChildrenBuilder.routeLink(href: String, className: String, id: String? = null, target: WindowTarget? = null, block: (ChildrenBuilder.() -> Unit)?) =
    routeLink(href, ClassName(className), id, target, block)

fun ChildrenBuilder.routeLink(href: String, className: ClassName? = null, id: String? = null, target: WindowTarget? = null, block: (ChildrenBuilder.() -> Unit)?) {
    Link {
        this.id = id
        to = href
        replace = false
        this.className = className
        this.target = target
        block?.invoke(this)
    }
}
