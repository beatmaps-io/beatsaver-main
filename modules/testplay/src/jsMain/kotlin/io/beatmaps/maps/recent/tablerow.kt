package io.beatmaps.maps.recent

import external.routeLink
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapVersion
import io.beatmaps.shared.map.diffIcons
import io.beatmaps.shared.map.links
import io.beatmaps.shared.map.uploaderWithInfo
import io.beatmaps.util.fcmemo
import react.Props
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.small
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.tr
import web.cssom.ClassName

external interface TableRowProps : Props {
    var map: MapDetail
    var version: MapVersion?
}

val beatmapTableRow = fcmemo<TableRowProps>("beatmapTableRow") { props ->
    tr {
        td {
            img {
                alt = "Cover Image"
                src = props.version?.coverURL
                className = ClassName("cover")
                width = 100.0
                height = 100.0
            }
        }
        td {
            routeLink(props.map.link()) {
                +props.map.name
            }
            p {
                uploaderWithInfo {
                    map = props.map
                    version = props.version
                }
                small {
                    +props.map.description.replace("\n", " ")
                }
            }
        }
        td {
            className = ClassName("diffs")
            diffIcons.invoke {
                diffs = props.version?.diffs
            }
        }
        td {
            className = ClassName("links")
            links {
                map = props.map
                version = props.version
            }
        }
    }
}
