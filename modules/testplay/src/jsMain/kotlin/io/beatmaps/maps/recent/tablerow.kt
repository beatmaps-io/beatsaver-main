package io.beatmaps.maps.recent

import external.routeLink
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapVersion
import io.beatmaps.shared.map.diffIcons
import io.beatmaps.shared.map.links
import io.beatmaps.shared.map.uploaderWithInfo
import react.Props
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.small
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.tr
import react.fc
import web.cssom.ClassName

external interface TableRowProps : Props {
    var map: MapDetail
    var version: MapVersion?
}

val beatmapTableRow = fc<TableRowProps>("beatmapTableRow") { props ->
    tr {
        td {
            img {
                attrs.alt = "Cover Image"
                attrs.src = props.version?.coverURL
                attrs.className = ClassName("cover")
                attrs.width = 100.0
                attrs.height = 100.0
            }
        }
        td {
            routeLink(props.map.link()) {
                +props.map.name
            }
            p {
                uploaderWithInfo {
                    attrs.map = props.map
                    attrs.version = props.version
                }
                small {
                    +props.map.description.replace("\n", " ")
                }
            }
        }
        td {
            attrs.className = ClassName("diffs")
            diffIcons.invoke {
                attrs.diffs = props.version?.diffs
            }
        }
        td {
            attrs.className = ClassName("links")
            links {
                attrs.map = props.map
                attrs.version = props.version
            }
        }
    }
}
