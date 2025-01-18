package io.beatmaps.user.list

import io.beatmaps.common.api.ApiOrder
import io.beatmaps.configContext
import io.beatmaps.util.fcmemo
import js.objects.jso
import react.Props
import react.dom.aria.AriaSort
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.th
import react.use
import web.cssom.ClassName
import web.cssom.px

external interface SortThProps : Props {
    var column: MapperColumn
    var sort: MapperColumn
    var updateSort: (MapperColumn, ApiOrder) -> Unit
    var order: ApiOrder
}

fun ApiOrder.toAriaSort() = when (this) {
    ApiOrder.DESC -> AriaSort.descending
    ApiOrder.ASC -> AriaSort.ascending
}

val sortTh = fcmemo<SortThProps>("sortTh") { props ->
    val config = use(configContext)
    th {
        if (config?.v2Search == true && props.column.sortEnum != null) {
            ariaSort = when (props.sort) {
                props.column -> props.order.toAriaSort()
                else -> AriaSort.none
            }
            onClick = {
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
            style = jso {
                width = w.px
            }
        }
        props.column.icon?.let { icon ->
            i {
                className = ClassName("fas $icon")
                title = props.column.text
            }
        } ?: run {
            +props.column.text
        }
    }
}
