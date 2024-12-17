package io.beatmaps.maps

import io.beatmaps.api.MapDetail
import kotlinx.html.id
import kotlinx.html.js.onClickFunction
import react.Props
import react.dom.a
import react.dom.li
import react.dom.span
import react.dom.ul
import react.fc

external interface MapPageNavProps : Props {
    var map: MapDetail
    var tab: MapTabs?
    var setTab: ((MapTabs) -> Unit)?
}

val mapPageNav = fc<MapPageNavProps> { props ->
    ul("nav nav-minimal mb-3") {
        MapTabs.entries.filter { it.enabled }.forEach { tab ->
            li("nav-item") {
                a("#", classes = "nav-link" + if (props.tab == tab) " active" else "") {
                    attrs.id = "nav-${tab.id}"
                    attrs.onClickFunction = { e ->
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
