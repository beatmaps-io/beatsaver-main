package io.beatmaps.index

import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapVersion
import react.RBuilder
import react.RComponent
import react.RProps
import react.RReadableRef
import react.RState
import react.ReactElement
import react.dom.img
import react.dom.p
import react.dom.small
import react.dom.td
import react.dom.tr
import react.router.dom.routeLink

external interface TableRowProps : RProps {
    var map: MapDetail
    var version: MapVersion?
    var modal: RReadableRef<ModalComponent>
}

@JsExport
class TableRow : RComponent<TableRowProps, RState>() {
    override fun RBuilder.render() {
        tr {
            td {
                img(src = props.version?.coverURL, alt = "Cover Image", classes = "cover") {
                    attrs.width = "100"
                    attrs.height = "100"
                }
            }
            td {
                routeLink("/maps/${props.map.id}") {
                    +props.map.name
                }
                p {
                    uploader {
                        attrs.map = props.map
                        attrs.version = props.version
                    }
                    small {
                        +props.map.description.replace("\n", " ")
                    }
                }
            }
            td("diffs") {
                diffIcons.invoke {
                    attrs.diffs = props.version?.diffs
                }
            }
            td("links") {
                links {
                    attrs.map = props.map
                    attrs.version = props.version
                    attrs.modal = props.modal
                }
            }
        }
    }
}

fun RBuilder.beatmapTableRow(handler: TableRowProps.() -> Unit): ReactElement {
    return child(TableRow::class) {
        this.attrs(handler)
    }
}
