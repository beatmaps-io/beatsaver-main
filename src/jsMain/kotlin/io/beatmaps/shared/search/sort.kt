package io.beatmaps.shared.search

import io.beatmaps.common.SearchOrder
import io.beatmaps.common.SortOrderTarget
import kotlinx.html.id
import kotlinx.html.js.onChangeFunction
import org.w3c.dom.HTMLSelectElement
import react.Props
import react.dom.attrs
import react.dom.option
import react.dom.select
import react.fc
import react.useEffect
import react.useState

external interface SortProps : Props {
    var cb: ((SearchOrder) -> Unit)?
    var default: SearchOrder?
    var target: SortOrderTarget
    var id: String?
}

val sort = fc<SortProps> { props ->
    val default = props.default ?: SearchOrder.Relevance
    val (sortOrder, setSortOrder) = useState(default)

    useEffect(props.default) {
        setSortOrder(default)
    }

    select("form-select") {
        attrs {
            id = props.id ?: ""
            attributes["aria-label"] = "Sort by"
            onChangeFunction = {
                val elem = it.currentTarget as HTMLSelectElement
                (SearchOrder.fromString(elem.value) ?: SearchOrder.Relevance).let { newOrder ->
                    setSortOrder(newOrder)
                    props.cb?.invoke(newOrder)
                }
            }
        }
        SearchOrder.entries.filter { props.target in it.targets }.forEach {
            option {
                attrs.value = it.toString()
                attrs.selected = sortOrder == it
                +it.toString()
            }
        }
    }
}
