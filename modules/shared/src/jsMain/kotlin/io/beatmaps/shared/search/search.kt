package io.beatmaps.shared.search

import external.Moment
import external.dates
import io.beatmaps.common.SearchOrder
import io.beatmaps.common.SortOrderTarget
import io.beatmaps.shared.form.multipleChoice
import io.beatmaps.shared.form.slider
import io.beatmaps.shared.form.toggle
import io.beatmaps.shared.loadingElem
import io.beatmaps.util.fcmemo
import js.objects.jso
import kotlinx.browser.document
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import react.Props
import react.RBuilder
import react.Suspense
import react.createElement
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.form
import react.dom.html.ReactHTML.h4
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.span
import react.useCallback
import react.useEffect
import react.useEffectWithCleanup
import react.useRef
import react.useState
import web.cssom.ClassName
import web.html.ButtonType
import web.html.InputType

enum class FilterCategory {
    NONE, GENERAL, REQUIREMENTS
}
abstract class FilterInfo<T, V>(val key: String) {
    abstract val name: String
    abstract val cat: FilterCategory
    abstract val fromParams: (T) -> V
    abstract val isFiltered: (Any?) -> Boolean

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class.js != other::class.js) return false

        other as FilterInfo<*, *>

        return key == other.key
    }

    override fun hashCode() = key.hashCode()
}

class BooleanFilterInfo<T>(key: String, override val name: String, override val cat: FilterCategory, override val fromParams: (T) -> Boolean) : FilterInfo<T, Boolean>(key) {
    override val isFiltered = { it: Any? -> it as? Boolean ?: false }
}

class MultipleChoiceFilterInfo<T, V>(key: String, override val name: String, override val cat: FilterCategory, val choices: Map<String, V>, val default: V, override val fromParams: (T) -> V) : FilterInfo<T, V>(key) {
    override val isFiltered = { it: Any? -> it != null && it != default }
}

fun interface SearchParamGenerator<T : CommonParams> {
    fun ISearch.get(): T
}

fun interface ExtraContentRenderer {
    fun RBuilder.invoke()
}

fun RBuilder.invokeECR(renderer: ExtraContentRenderer?) {
    renderer?.let { exr ->
        with(exr) {
            this@invokeECR.invoke()
        }
    }
}

fun <T : FilterInfo<*, *>, V> Map<T, V>.getByKeyOrNull(key: String): V? =
    entries.firstOrNull { it.key.key == key }?.value

interface ISearch {
    val minNps: Float
    val maxNps: Float
    val startDate: Moment?
    val endDate: Moment?
    val order: SearchOrder
    fun searchText(): String
    fun isFiltered(key: String): Boolean
    fun filterOrNull(key: String): Any?
}

external interface SearchProps<T : CommonParams> : Props {
    var typedState: T?
    var sortOrderTarget: SortOrderTarget
    var maxNps: Int
    var filters: List<FilterInfo<T, *>>
    var paramsFromPage: SearchParamGenerator<T>
    var updateSearchParams: (T, Int?) -> Unit
    var updateUI: ((T?) -> Unit)?
    var filterTexts: (() -> List<String>)?
    var extraFilters: ExtraContentRenderer?
}

fun <T : CommonParams> generateSearchComponent(name: String) = fcmemo<SearchProps<T>>("${name}Search") { props ->
    val filterRefs = props.filters.associateWith { useRef<HTMLInputElement>() }

    val inputRef = useRef<HTMLInputElement>()
    val dropdownRef = useRef<HTMLButtonElement>()
    val dropdownDivRef = useRef<HTMLDivElement>()

    val (minNps, setMinNps) = useState(0f)
    val (maxNps, setMaxNps) = useState(props.maxNps.toFloat())
    val (filterMap, setFilterMap) = useState(mapOf<FilterInfo<T, *>, Any?>())
    val (order, setOrder) = useState(SearchOrder.Relevance)
    val (focusedInput, setFocusedInput) = useState<String>()
    val (startDate, setStartDate) = useState<Moment>()
    val (endDate, setEndDate) = useState<Moment>()
    val (filtersOpen, setFiltersOpen) = useState(false)

    val stopProp = { it: Event ->
        it.stopPropagation()
    }

    val hideFilters = { _: Event ->
        if (filtersOpen) setFiltersOpen(false)
    }

    fun updateUI() {
        val fromParams = props.typedState
        inputRef.current?.value = fromParams?.search ?: ""
        val newFilterMap = filterMap.toMutableMap()

        filterRefs.forEach { (filter, filterRef) ->
            if (filter is BooleanFilterInfo) {
                val newState = fromParams?.let { params -> filter.fromParams(params) } ?: false
                filterRef.current?.checked = newState
                newFilterMap[filter] = newState
            } else if (filter is MultipleChoiceFilterInfo) {
                val newState = fromParams?.let { params -> filter.fromParams(params) }
                newFilterMap[filter] = newState
            }
        }

        setFilterMap(newFilterMap)
        setMinNps(fromParams?.minNps ?: 0f)
        setMaxNps(fromParams?.maxNps ?: props.maxNps.toFloat())
        setOrder(fromParams?.sortOrder ?: order)
        setStartDate(fromParams?.from?.let { Moment(it) })
        setEndDate(fromParams?.to?.let { Moment(it) })

        props.updateUI?.invoke(fromParams)
    }

    useEffect(props.typedState) {
        updateUI()
    }

    useEffectWithCleanup {
        dropdownRef.current?.addEventListener("mouseup", stopProp)
        dropdownDivRef.current?.addEventListener("mouseup", stopProp)
        document.addEventListener("mouseup", hideFilters)

        onCleanup {
            document.removeEventListener("mouseup", hideFilters)
            dropdownDivRef.current?.addEventListener("mouseup", stopProp)
            dropdownRef.current?.removeEventListener("mouseup", stopProp)
        }
    }

    val searchHelper = object : ISearch {
        override val minNps = minNps
        override val maxNps = maxNps
        override val startDate = startDate
        override val endDate = endDate
        override val order = order
        override fun searchText() = inputRef.current?.value?.trim() ?: ""
        override fun isFiltered(key: String): Boolean {
            return filterMap.entries.firstOrNull { it.key.key == key }?.let { (filter, value) -> filter.isFiltered(value) } ?: false
        }
        override fun filterOrNull(key: String) = filterMap.getByKeyOrNull(key)
    }

    form {
        div {
            attrs.className = ClassName("row")
            div {
                attrs.className = ClassName("mb-3 col-lg-9")
                input {
                    attrs.type = InputType.search
                    attrs.className = ClassName("form-control")
                    attrs.placeholder = "Search"
                    attrs.ariaLabel = "Search"
                    ref = inputRef
                }
            }
            div {
                attrs.className = ClassName("mb-3 col-lg-3 btn-group")
                button {
                    attrs.type = ButtonType.submit
                    attrs.className = ClassName("btn btn-primary")
                    attrs.onClick = {
                        it.preventDefault()
                        val newState = with(props.paramsFromPage) {
                            searchHelper.get()
                        }

                        props.updateSearchParams(newState, null)
                    }
                    +"Search"
                }
            }
        }
        div {
            attrs.className = ClassName("row")
            div {
                attrs.className = ClassName("filter-container col-sm-3")
                button {
                    attrs.className = ClassName("filter-dropdown")
                    attrs.onClick = {
                        it.preventDefault()
                        setFiltersOpen(!filtersOpen)
                    }
                    ref = dropdownRef
                    span {
                        val filters = filterMap.entries.filter { (filter, value) -> filter.isFiltered(value) }.map { it.key.name } +
                            (props.filterTexts?.invoke() ?: listOf())

                        if (filters.isEmpty()) {
                            +"Filters"
                        } else {
                            +filters.joinToString(", ")
                        }
                    }
                    i {
                        attrs.className = ClassName("fas fa-angle-" + if (filtersOpen) "up" else "down")
                    }
                }
                div {
                    ref = dropdownDivRef
                    attrs.className = ClassName("dropdown-menu" + if (filtersOpen) " show" else "")

                    div {
                        attrs.className = ClassName("d-flex")
                        div {
                            filterRefs.entries.fold(FilterCategory.NONE) { cat, (filter, filterRef) ->
                                if (cat != filter.cat) {
                                    h4 {
                                        attrs.className = ClassName(if (cat == FilterCategory.NONE) "" else "mt-4")
                                        +filter.cat.toString()
                                    }
                                }

                                if (filter is BooleanFilterInfo) {
                                    toggle {
                                        attrs.id = filter.key
                                        attrs.text = filter.name
                                        attrs.toggleRef = filterRef
                                        attrs.block = {
                                            setFilterMap(filterMap.plus(filter to it))
                                        }
                                    }
                                } else if (filter is MultipleChoiceFilterInfo) {
                                    multipleChoice {
                                        attrs.choices = filter.choices
                                        attrs.name = filter.key
                                        attrs.selectedValue = filterMap.getByKeyOrNull(filter.key) ?: filter.default
                                        attrs.block = {
                                            setFilterMap(filterMap.plus(filter to it))
                                        }
                                    }
                                }

                                filter.cat
                            }
                        }

                        invokeECR(props.extraFilters)
                    }
                }
            }
            slider {
                attrs.text = "NPS"
                attrs.currentMin = minNps
                attrs.currentMax = maxNps
                attrs.max = props.maxNps
                attrs.block = {
                    setMinNps(it[0] / 10f)
                    setMaxNps(it[1] / 10f)
                }
                attrs.className = ClassName("mb-3 col-sm-3")
            }
            div {
                attrs.className = ClassName("mb-3 col-sm-3")
                Suspense {
                    attrs.fallback = loadingElem
                    dates.dateRangePicker {
                        attrs.startDate = startDate
                        attrs.endDate = endDate
                        attrs.startDateId = "startobj"
                        attrs.endDateId = "endobj"
                        attrs.onFocusChange = useCallback {
                            setFocusedInput(it)
                        }
                        attrs.onDatesChange = useCallback {
                            setStartDate(it.startDate)
                            setEndDate(it.endDate)
                        }
                        attrs.isOutsideRange = { false }
                        attrs.focusedInput = focusedInput
                        attrs.displayFormat = "DD/MM/YYYY"
                        attrs.small = true
                        attrs.numberOfMonths = 1
                        attrs.renderCalendarInfo = useCallback {
                            createElement(
                                presets,
                                jso {
                                    callback = { sd, ed ->
                                        setStartDate(sd)
                                        setEndDate(ed)
                                    }
                                }
                            )
                        }
                    }
                }
            }
            div {
                attrs.className = ClassName("mb-3 col-sm-3")
                sort {
                    attrs.target = props.sortOrderTarget
                    attrs.cb = {
                        setOrder(it)
                    }
                    attrs.default = order
                }
            }
        }
    }
}
