package io.beatmaps.maps

import io.beatmaps.api.MapDetail
import io.beatmaps.util.fcmemo
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.ul
import web.cssom.ClassName

external interface MapPageNavProps : Props {
    var map: MapDetail
    var tab: MapTabs?
    var setTab: ((MapTabs) -> Unit)?
}

val mapPageNav = fcmemo<MapPageNavProps>("mapPageNav") { props ->
    ul {
        className = ClassName("nav nav-minimal mb-3")
        MapTabs.entries.filter { it.enabled }.forEach { tab ->
            li {
                className = ClassName("nav-item")
                a {
                    href = "#"
                    className = ClassName("nav-link" + if (props.tab == tab) " active" else "")
                    id = "nav-${tab.id}"
                    onClick = { e ->
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
