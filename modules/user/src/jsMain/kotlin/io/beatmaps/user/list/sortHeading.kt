package io.beatmaps.user.list

import io.beatmaps.common.api.ApiOrder
import io.beatmaps.configContext
import io.beatmaps.util.fcmemo
import js.objects.jso
import react.Props
import react.dom.aria.AriaSort
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.th
import react.useContext
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
    val config = useContext(configContext)
    th {
        if (config?.v2Search == true && props.column.sortEnum != null) {
            attrs.ariaSort = when (props.sort) {
                props.column -> props.order.toAriaSort()
                else -> AriaSort.none
            }
            attrs.onClick = {
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
            attrs.style = jso {
                width = w.px
            }
        }
        props.column.icon?.let { icon ->
            i {
                attrs.className = ClassName("fas $icon")
                attrs.title = props.column.text
            }
        } ?: run {
            +props.column.text
        }
    }
}
