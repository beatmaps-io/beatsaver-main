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
    var dark: Boolean?
}

val sort = fc<SortProps>("sort") { props ->
    val default = props.default ?: SearchOrder.Relevance
    val (sortOrder, setSortOrder) = useState(default)

    useEffect(props.default) {
        setSortOrder(default)
    }

    val classes = listOfNotNull(
        "form-select",
        if (props.dark == true) "dark" else null
    ).joinToString(" ")

    select(classes) {
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
            value = sortOrder.toString()
        }
        SearchOrder.entries.filter { props.target in it.targets }.forEach {
            option {
                attrs.value = it.toString()
                +it.toString()
            }
        }
    }
}
