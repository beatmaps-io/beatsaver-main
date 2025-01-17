package io.beatmaps.maps

import io.beatmaps.api.MapDetail
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.ul
import react.fc
import web.cssom.ClassName

external interface MapPageNavProps : Props {
    var map: MapDetail
    var tab: MapTabs?
    var setTab: ((MapTabs) -> Unit)?
}

val mapPageNav = fc<MapPageNavProps>("mapPageNav") { props ->
    ul {
        attrs.className = ClassName("nav nav-minimal mb-3")
        MapTabs.entries.filter { it.enabled }.forEach { tab ->
            li {
                attrs.className = ClassName("nav-item")
                a {
                    attrs.href = "#"
                    attrs.className = ClassName("nav-link" + if (props.tab == tab) " active" else "")
                    attrs.id = "nav-${tab.id}"
                    attrs.onClick = { e ->
                        e.preventDefault()
                        props.setTab?.invoke(tab)
                    }
                    span {
                        +tab.name
                    }
                }
            }
        }
    }
}
