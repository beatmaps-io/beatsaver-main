package io.beatmaps.shared

import external.DateRangePicker
import external.Moment
import io.beatmaps.api.SearchOrder
import io.beatmaps.api.SortOrderTarget
import io.beatmaps.common.MapTag
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import react.FC
import react.RBuilder
import react.RComponent
import react.RProps
import react.RReadableRef
import react.RState
import react.ReactElement
import react.createRef
import react.dom.button
import react.dom.div
import react.dom.form
import react.dom.i
import react.dom.input
import react.dom.option
import react.dom.select
import react.dom.span
import react.setState

data class FilterInfo<T>(val key: String, val name: String, val cat: FilterCategory, val fromParams: (T) -> Boolean)

external interface FilterBodyProps<T> : RProps {
    var filterRefs: Map<FilterInfo<T>, RReadableRef<HTMLInputElement>>
    var tags: Map<Boolean, Set<MapTag>>?
    var setState: (Search<T>.() -> Unit) -> Unit
    var modifyTags: (MapTag) -> Unit
}

data class SearchProps<T> (
    var sortOrderTarget: SortOrderTarget,
    var maxNps: Int,
    var filters: List<FilterInfo<T>>,
    var filterBody: FC<FilterBodyProps<T>>,
    var getSearchParams: Search<T>.() -> T,
    var updateSearchParams: (T) -> Unit
) : RProps

data class SearchState<T>(
    var minNps: Float = 0f,
    var maxNps: Float = 16f,
    val filterMap: MutableMap<FilterInfo<T>, Boolean> = mutableMapOf(),
    var order: SearchOrder = SearchOrder.Relevance,
    var focusedInput: String? = null,
    var startDate: Moment? = null,
    var endDate: Moment? = null,
    var filtersOpen: Boolean = false,
    var tags: Map<Boolean, Set<MapTag>>? = null,
    var shiftHeld: Boolean = false,
    var altHeld: Boolean = false
) : RState
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

inline fun <T> T.applyIf(condition: Boolean, block: T.() -> T): T = if (condition) block(this) else this

open class Search<T>(props: SearchProps<T>) : RComponent<SearchProps<T>, SearchState<T>>(props) {
    val filterRefs = props.filters.associateWith { createRef<HTMLInputElement>() }

    val inputRef = createRef<HTMLInputElement>()
    val sortRef = createRef<HTMLSelectElement>()
    private val dropdownRef = createRef<HTMLButtonElement>()
    private val dropdownDivRef = createRef<HTMLDivElement>()

    init {
        state = SearchState()
    }

    override fun componentWillMount() {
        setState {
            maxNps = props.maxNps.toFloat()
        }
    }

    override fun componentDidMount() {
        dropdownRef.current?.addEventListener("mouseup", ::stopProp)
        dropdownDivRef.current?.addEventListener("mouseup", ::stopProp)
        document.addEventListener("mouseup", ::hideFilters)
        document.addEventListener("keyup", ::handleShift)
        document.addEventListener("keydown", ::handleShift)
    }

    private fun stopProp(it: Event) {
        it.stopPropagation()
    }

    private fun hideFilters(it: Event) {
        setState {
            filtersOpen = false
        }
    }

    private fun handleShift(it: Event) {
        setState {
            shiftHeld = (it as? KeyboardEvent)?.shiftKey ?: false
            altHeld = (it as? KeyboardEvent)?.altKey ?: false
        }
    }

    override fun componentWillUnmount() {
        document.removeEventListener("mouseup", ::hideFilters)
        document.removeEventListener("keyup", ::handleShift)
        document.removeEventListener("keydown", ::handleShift)
        dropdownDivRef.current?.addEventListener("mouseup", ::stopProp)
        dropdownRef.current?.removeEventListener("mouseup", ::stopProp)
    }

    fun isFiltered(s: String) =
        props.filters.first { it.key == s }.let { filter ->
            state.filterMap.getOrElse(filter) { false }
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
                            props.updateSearchParams(
                                props.getSearchParams(this@Search)
                            )
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
                                filtersOpen = !state.filtersOpen
                            }
                        }
                        ref = dropdownRef
                        span {
                            val filters = filterRefs.filter { state.filterMap.getOrElse(it.key) { false } }.map { it.key.name } +
                                (state.tags?.flatMap { y -> y.value.map { z -> (if (y.key) "" else "!") + z.slug } } ?: listOf())

                            if (filters.isEmpty()) {
                                +"Filters"
                            } else {
                                +filters.joinToString(", ")
                            }
                        }
                        i("fas fa-angle-" + if (state.filtersOpen) "up" else "down") {}
                    }
                    div("dropdown-menu" + if (state.filtersOpen) " show" else "") {
                        ref = dropdownDivRef

                        props.filterBody {
                            attrs.filterRefs = filterRefs
                            attrs.tags = state.tags
                            attrs.setState = { handler ->
                                setState {
                                    handler()
                                }
                            }
                            attrs.modifyTags = {
                                (state.tags?.get(!state.altHeld) ?: setOf()).let { tags ->
                                    val shouldAdd = !tags.contains(it)

                                    val newTags = tags.applyIf(!state.shiftHeld) {
                                        filterTo(hashSetOf()) { o -> o.type != it.type }
                                    }.applyIf(shouldAdd) {
                                        plus(it)
                                    }.applyIf(state.shiftHeld && !shouldAdd) {
                                        minus(it)
                                    }

                                    setState {
                                        this.tags = mapOf(
                                            !state.altHeld to newTags,
                                            state.altHeld to (state.tags?.get(state.altHeld)?.let { x -> x - it } ?: setOf())
                                        )
                                    }
                                    window.asDynamic().getSelection().removeAllRanges()
                                }
                            }
                        }
                    }
                }
                slider("NPS", state.minNps, state.maxNps, props.maxNps) {
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
                                println("$it: ${state.order == it}")
                                attrs.selected = state.order == it
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

inline fun <T, reified S : Search<T>> RBuilder.searchTyped(noinline handler: SearchProps<T>.() -> Unit): ReactElement {
    return child(S::class) {
        this.attrs(handler)
    }
}

fun <T> RBuilder.search(handler: SearchProps<T>.() -> Unit) = searchTyped(handler)
