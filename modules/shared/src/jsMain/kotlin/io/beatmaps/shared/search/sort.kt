package io.beatmaps.shared.search

import io.beatmaps.common.SearchOrder
import io.beatmaps.common.SortOrderTarget
import io.beatmaps.util.fcmemo
import react.Props
import react.dom.html.ReactHTML.option
import react.dom.html.ReactHTML.select
import react.useEffect
import react.useState
import web.cssom.ClassName

external interface SortProps : Props {
    var cb: ((SearchOrder) -> Unit)?
    var default: SearchOrder?
    var target: SortOrderTarget
    var id: String?
    var dark: Boolean?
}

val sort = fcmemo<SortProps>("sort") { props ->
    val default = props.default ?: SearchOrder.Relevance
    val (sortOrder, setSortOrder) = useState(default)

    useEffect(props.default) {
        setSortOrder(default)
    }

    val classes = ClassName(
        listOfNotNull(
            "form-select",
            if (props.dark == true) "dark" else null
        ).joinToString(" ")
    )

    select {
        className = classes
        id = props.id ?: ""
        ariaLabel = "Sort by"
        onChange = {
            (SearchOrder.fromString(it.currentTarget.value) ?: SearchOrder.Relevance).let { newOrder ->
                setSortOrder(newOrder)
                props.cb?.invoke(newOrder)
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
}
