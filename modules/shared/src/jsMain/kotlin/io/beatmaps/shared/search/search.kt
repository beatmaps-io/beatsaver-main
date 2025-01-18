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
import react.Props
import react.Suspense
import react.createElement
import react.dom.html.HTMLAttributes
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
import web.dom.document
import web.events.addEventListener
import web.events.removeEventListener
import web.html.ButtonType
import web.html.HTMLButtonElement
import web.html.HTMLDivElement
import web.html.HTMLInputElement
import web.html.InputType
import web.uievents.MouseEvent

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
    fun HTMLAttributes<*>.invoke()
}

fun HTMLAttributes<*>.invokeECR(renderer: ExtraContentRenderer?) {
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

    val stopProp = { it: MouseEvent ->
        it.stopPropagation()
    }

    val hideFilters = { _: MouseEvent ->
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
        dropdownRef.current?.addEventListener(MouseEvent.MOUSE_UP, stopProp)
        dropdownDivRef.current?.addEventListener(MouseEvent.MOUSE_UP, stopProp)
        document.addEventListener(MouseEvent.MOUSE_UP, hideFilters)

        onCleanup {
            document.removeEventListener(MouseEvent.MOUSE_UP, hideFilters)
            dropdownDivRef.current?.removeEventListener(MouseEvent.MOUSE_UP, stopProp)
            dropdownRef.current?.removeEventListener(MouseEvent.MOUSE_UP, stopProp)
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
            className = ClassName("row")
            div {
                className = ClassName("mb-3 col-lg-9")
                input {
                    type = InputType.search
                    className = ClassName("form-control")
                    placeholder = "Search"
                    ariaLabel = "Search"
                    ref = inputRef
                }
            }
            div {
                className = ClassName("mb-3 col-lg-3 btn-group")
                button {
                    type = ButtonType.submit
                    className = ClassName("btn btn-primary")
                    onClick = {
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
            className = ClassName("row")
            div {
                className = ClassName("filter-container col-sm-3")
                button {
                    className = ClassName("filter-dropdown")
                    onClick = {
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
                        className = ClassName("fas fa-angle-" + if (filtersOpen) "up" else "down")
                    }
                }
                div {
                    ref = dropdownDivRef
                    className = ClassName("dropdown-menu" + if (filtersOpen) " show" else "")

                    div {
                        className = ClassName("d-flex")
                        div {
                            filterRefs.entries.fold(FilterCategory.NONE) { cat, (filter, filterRef) ->
                                if (cat != filter.cat) {
                                    h4 {
                                        className = ClassName(if (cat == FilterCategory.NONE) "" else "mt-4")
                                        +filter.cat.toString()
                                    }
                                }

                                if (filter is BooleanFilterInfo) {
                                    toggle {
                                        id = filter.key
                                        text = filter.name
                                        toggleRef = filterRef
                                        block = {
                                            setFilterMap(filterMap.plus(filter to it))
                                        }
                                    }
                                } else if (filter is MultipleChoiceFilterInfo) {
                                    multipleChoice {
                                        choices = filter.choices
                                        this.name = filter.key
                                        selectedValue = filterMap.getByKeyOrNull(filter.key) ?: filter.default
                                        block = {
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
                text = "NPS"
                currentMin = minNps
                currentMax = maxNps
                max = props.maxNps
                block = {
                    setMinNps(it[0] / 10f)
                    setMaxNps(it[1] / 10f)
                }
                className = ClassName("mb-3 col-sm-3")
            }
            div {
                className = ClassName("mb-3 col-sm-3")
                Suspense {
                    fallback = loadingElem
                    dates.dateRangePicker {
                        this.startDate = startDate
                        this.endDate = endDate
                        startDateId = "startobj"
                        endDateId = "endobj"
                        onFocusChange = useCallback {
                            setFocusedInput(it)
                        }
                        onDatesChange = useCallback {
                            setStartDate(it.startDate)
                            setEndDate(it.endDate)
                        }
                        isOutsideRange = { false }
                        this.focusedInput = focusedInput
                        displayFormat = "DD/MM/YYYY"
                        small = true
                        numberOfMonths = 1
                        renderCalendarInfo = useCallback {
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
                className = ClassName("mb-3 col-sm-3")
                sort {
                    target = props.sortOrderTarget
                    cb = {
                        setOrder(it)
                    }
                    default = order
                }
            }
        }
    }
}
