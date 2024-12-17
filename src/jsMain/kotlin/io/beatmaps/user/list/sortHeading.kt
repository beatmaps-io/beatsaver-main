package io.beatmaps.user.list

import io.beatmaps.common.api.ApiOrder
import io.beatmaps.configContext
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import react.Props
import react.dom.i
import react.dom.jsStyle
import react.dom.th
import react.fc
import react.useContext

external interface SortThProps : Props {
    var column: MapperColumn
    var sort: MapperColumn
    var updateSort: (MapperColumn, ApiOrder) -> Unit
    var order: ApiOrder
}

val sortTh = fc<SortThProps> { props ->
    val config = useContext(configContext)
    th {
        if (config?.v2Search == true && props.column.sortEnum != null) {
            attrs.attributes["aria-sort"] = when (props.sort) {
                props.column -> props.order.aria
                else -> "none"
            }
            attrs.onClickFunction = {
                props.updateSort(
                    props.column,
                    if (props.column == props.sort) {
                        props.order.invert()
                    } else {
                        ApiOrder.DESC
                    }
                )
            }
        }
        props.column.width?.let { w ->
            attrs.jsStyle {
                width = "${w}px"
            }
        }
        props.column.icon?.let { icon ->
            i("fas $icon") { attrs.title = props.column.text }
        } ?: run {
            +props.column.text
        }
    }
}
