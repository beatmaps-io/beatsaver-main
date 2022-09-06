package io.beatmaps.shared

import external.DateRangePicker
import external.Moment
import io.beatmaps.api.SearchOrder
import io.beatmaps.api.SortOrderTarget
import io.beatmaps.index.encodeURIComponent
import kotlinx.browser.document
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.events.Event
import react.FC
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
import react.createRef
import react.dom.button
import react.dom.div
import react.dom.form
import react.dom.h4
import react.dom.i
import react.dom.input
import react.dom.option
import react.dom.select
import react.dom.span
import react.router.dom.RouteResultHistory
import react.setState

data class FilterInfo<T>(val key: String, val name: String, val cat: FilterCategory, val fromParams: (T) -> Boolean)

fun interface SearchParamGenerator<T : CommonParams> {
    fun Search<T>.get(): T
}

external interface SearchProps<T : CommonParams> : RProps {
    var typedState: T?
    var sortOrderTarget: SortOrderTarget
    var maxNps: Int
    var filters: List<FilterInfo<T>>
    var paramsFromPage: SearchParamGenerator<T>
    var updateSearchParams: (T, Int?) -> Unit
    var updateUI: ((T?) -> Unit)?
    var filterTexts: (() -> List<String>)?
    var extraFilters: FC<RProps>?
}

external interface SearchState<T> : RState {
    var minNps: Float?
    var maxNps: Float?
    var filterMap: MutableMap<FilterInfo<T>, Boolean>?
    var order: SearchOrder?
    var focusedInput: String?
    var startDate: Moment?
    var endDate: Moment?
    var filtersOpen: Boolean?
}
data class PresetDateRange(val startDate: Moment?, val endDate: Moment?)

val presets = mapOf(
    "Last 24h" to PresetDateRange(Moment().subtract(1, "day"), null),
    "Last week" to PresetDateRange(Moment().subtract(1, "week"), null),
    "Last month" to PresetDateRange(Moment().subtract(1, "month"), null),
    "Last 3 months" to PresetDateRange(Moment().subtract(3, "month"), null),
    "All" to PresetDateRange(null, null),
)

enum class FilterCategory {
    NONE, GENERAL, REQUIREMENTS
}

interface CommonParams {
    val search: String
    val sortOrder: SearchOrder
    val minNps: Float?
    val maxNps: Float?
    val from: String?
    val to: String?
}

fun includeIfNotNull(v: Any?, name: String) = if (v != null) "$name=$v" else null
fun CommonParams.queryParams() = listOfNotNull(
    (if (search.isNotBlank()) "q=${encodeURIComponent(search)}" else null),
    includeIfNotNull(maxNps, "maxNps"),
    includeIfNotNull(minNps, "minNps"),
    (if (sortOrder != SearchOrder.Relevance) "order=$sortOrder" else null),
    includeIfNotNull(from, "from"),
    includeIfNotNull(to, "to")
).toTypedArray()
fun <T> T.buildURL(parts: List<String>, root: String = "", row: Int?, state: T?, history: RouteResultHistory) =
    ((if (parts.isEmpty()) "/$root" else "?" + parts.joinToString("&")) + (row?.let { "#$it" } ?: "")).let { newUrl ->
        if (this == state) {
            history.replace(newUrl)
        } else {
            history.push(newUrl)
        }
    }

open class Search<T : CommonParams>(props: SearchProps<T>) : RComponent<SearchProps<T>, SearchState<T>>(props) {
    private val filterRefs = props.filters.associateWith { createRef<HTMLInputElement>() }

    val inputRef = createRef<HTMLInputElement>()
    private val sortRef = createRef<HTMLSelectElement>()
    private val dropdownRef = createRef<HTMLButtonElement>()
    private val dropdownDivRef = createRef<HTMLDivElement>()

    override fun componentWillMount() {
        setState {
            maxNps = props.maxNps.toFloat()
            filterMap = mutableMapOf()
        }
    }

    override fun componentDidMount() {
        updateUI()
        dropdownRef.current?.addEventListener("mouseup", stopProp)
        dropdownDivRef.current?.addEventListener("mouseup", stopProp)
        document.addEventListener("mouseup", hideFilters)
    }

    override fun componentDidUpdate(prevProps: SearchProps<T>, prevState: SearchState<T>, snapshot: Any) {
        if (prevProps.typedState != props.typedState) {
            updateUI()
        }
    }

    private fun updateUI() {
        val fromParams = props.typedState
        inputRef.current?.value = fromParams?.search ?: ""
        sortRef.current?.selectedIndex = (fromParams?.sortOrder ?: SearchOrder.Relevance).idx

        setState {
            filterRefs.forEach {
                val newState = fromParams?.let { params -> it.key.fromParams(params) } ?: false
                it.value.current?.checked = newState
                filterMap?.put(it.key, newState)
            }

            minNps = fromParams?.minNps ?: 0f
            maxNps = fromParams?.maxNps ?: props.maxNps.toFloat()
            order = fromParams?.sortOrder
            startDate = fromParams?.from?.let { Moment(it) }
            endDate = fromParams?.to?.let { Moment(it) }
        }

        props.updateUI?.invoke(fromParams)
    }

    private val stopProp = { it: Event ->
        it.stopPropagation()
    }

    private val hideFilters = { _: Event ->
        setState {
            filtersOpen = false
        }
    }

    override fun componentWillUnmount() {
        document.removeEventListener("mouseup", hideFilters)
        dropdownDivRef.current?.addEventListener("mouseup", stopProp)
        dropdownRef.current?.removeEventListener("mouseup", stopProp)
    }

    fun isFiltered(s: String) =
        props.filters.first { it.key == s }.let { filter ->
            state.filterMap?.get(filter) ?: false
        }

    override fun RBuilder.render() {
        form("") {
            div("row") {
                div("mb-3 col-lg-9") {
                    input(InputType.search, classes = "form-control") {
                        attrs.placeholder = "Search"
                        attrs.attributes["aria-label"] = "Search"
                        ref = inputRef
                    }
                }
                div("mb-3 col-lg-3 d-grid") {
                    button(type = ButtonType.submit, classes = "btn btn-primary") {
                        attrs.onClickFunction = {
                            it.preventDefault()
                            val newState = with(props.paramsFromPage) {
                                this@Search.get()
                            }

                            props.updateSearchParams(newState, null)
                        }
                        +"Search"
                    }
                }
            }
            div("row") {
                div("filter-container col-sm-3") {
                    button(classes = "filter-dropdown") {
                        attrs.onClickFunction = {
                            it.preventDefault()
                            setState {
                                filtersOpen = state.filtersOpen != true
                            }
                        }
                        ref = dropdownRef
                        span {
                            val filters = filterRefs.filter { state.filterMap?.get(it.key) ?: false }.map { it.key.name } +
                                (props.filterTexts?.invoke() ?: listOf())

                            if (filters.isEmpty()) {
                                +"Filters"
                            } else {
                                +filters.joinToString(", ")
                            }
                        }
                        i("fas fa-angle-" + if (state.filtersOpen == true) "up" else "down") {}
                    }
                    div("dropdown-menu" + if (state.filtersOpen == true) " show" else "") {
                        ref = dropdownDivRef

                        div("d-flex") {
                            div {
                                filterRefs.entries.fold(FilterCategory.NONE) { cat, filter ->
                                    if (cat != filter.key.cat) {
                                        h4(if (cat == FilterCategory.NONE) "" else "mt-4") {
                                            +filter.key.cat.toString()
                                        }
                                    }

                                    toggle(filter.key.key, filter.key.name, filter.value) {
                                        setState {
                                            filterMap?.put(filter.key, it)
                                        }
                                    }

                                    filter.key.cat
                                }
                            }

                            props.extraFilters?.invoke { }
                        }
                    }
                }
                slider("NPS", state.minNps ?: 0f, state.maxNps ?: 16f, props.maxNps) {
                    setState {
                        minNps = it[0] / 10f
                        maxNps = it[1] / 10f
                    }
                }
                div("mb-3 col-sm-3") {
                    DateRangePicker.default {
                        attrs.startDate = state.startDate
                        attrs.endDate = state.endDate
                        attrs.startDateId = "startobj"
                        attrs.endDateId = "endobj"
                        attrs.onFocusChange = {
                            setState {
                                focusedInput = it
                            }
                        }
                        attrs.onDatesChange = {
                            setState {
                                startDate = it.startDate
                                endDate = it.endDate
                            }
                        }
                        attrs.isOutsideRange = { false }
                        attrs.focusedInput = state.focusedInput
                        attrs.displayFormat = "DD/MM/YYYY"
                        attrs.small = true
                        attrs.numberOfMonths = 1
                        attrs.renderCalendarInfo = {
                            div("presets") {
                                presets.forEach { preset ->
                                    button {
                                        attrs.onClickFunction = {
                                            it.preventDefault()
                                            setState {
                                                startDate = preset.value.startDate
                                                endDate = preset.value.endDate
                                            }
                                        }
                                        +preset.key
                                    }
                                }
                            }
                        }
                    }
                }
                div("mb-3 col-sm-3") {
                    select("form-select") {
                        ref = sortRef
                        attrs.attributes["aria-label"] = "Sort by"
                        attrs.onChangeFunction = {
                            setState {
                                order = SearchOrder.fromString(sortRef.current?.value ?: "") ?: SearchOrder.Relevance
                            }
                        }
                        SearchOrder.values().filter { props.sortOrderTarget in it.targets }.forEach {
                            option {
                                attrs.selected = (state.order ?: SearchOrder.Relevance) == it
                                attrs.value = it.toString()
                                +it.toString()
                            }
                        }
                    }
                }
            }
        }
    }
}

inline fun <T : CommonParams, reified S : Search<T>> RBuilder.searchTyped(noinline handler: SearchProps<T>.() -> Unit): ReactElement {
    return child(S::class) {
        this.attrs(handler)
    }
}

fun <T : CommonParams> RBuilder.search(handler: SearchProps<T>.() -> Unit) = searchTyped(handler)
