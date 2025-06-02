package io.beatmaps.shared.search

import io.beatmaps.common.SearchOrder
import io.beatmaps.common.SortOrderTarget
import io.beatmaps.util.fcmemo
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.option
import react.dom.html.ReactHTML.select
import react.useEffect
import react.useState
import web.cssom.ClassName

external interface SortProps : Props {
    var cb: ((SearchOrder, Boolean) -> Unit)?
    var default: SearchOrder?
    var defaultAsc: Boolean?
    var target: SortOrderTarget
    var id: String?
    var dark: Boolean?
}

val sort = fcmemo<SortProps>("sort") { props ->
    val default = props.default ?: SearchOrder.Relevance
    val (sortOrder, setSortOrder) = useState(default)

    val defaultAsc = props.defaultAsc ?: false
    val (ascending, setAscending) = useState(defaultAsc)

    useEffect(props.default) {
        setSortOrder(default)
    }

    useEffect(props.defaultAsc) {
        setAscending(defaultAsc)
    }

    val classes = ClassName(
        listOfNotNull(
            "form-select",
            if (props.dark == true) "dark" else null
        ).joinToString(" ")
    )

    div {
        className = ClassName("d-flex")
        select {
            className = classes
            id = props.id ?: ""
            ariaLabel = "Sort by"
            onChange = {
                (SearchOrder.fromString(it.currentTarget.value) ?: SearchOrder.Relevance).let { newOrder ->
                    setSortOrder(newOrder)
                    props.cb?.invoke(newOrder, ascending)
                }
            }
            value = sortOrder.toString()
            SearchOrder.entries.filter { props.target in it.targets }.forEach {
                option {
                    value = it.toString()
                    +it.toString()
                }
            }
        }

        button {
            className = ClassName("btn btn-info ms-1")
            onClick = {
                it.preventDefault()
                setAscending(!ascending)
                props.cb?.invoke(sortOrder, !ascending)
            }
            i {
                className = ClassName("fas fa-sort-amount-${if (ascending) "up" else "down"}")
            }
        }
    }
}
