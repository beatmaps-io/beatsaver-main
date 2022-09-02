package io.beatmaps.index

import io.beatmaps.api.SearchOrder
import io.beatmaps.api.SortOrderTarget
import io.beatmaps.common.MapTag
import io.beatmaps.common.MapTagType
import io.beatmaps.dateFormat
import io.beatmaps.maps.mapTag
import io.beatmaps.setPageTitle
import io.beatmaps.shared.FilterCategory
import io.beatmaps.shared.FilterInfo
import io.beatmaps.shared.SearchParamGenerator
import io.beatmaps.shared.search
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.url.URLSearchParams
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.createRef
import react.dom.div
import react.dom.h4
import react.functionComponent
import react.ref
import react.router.dom.RouteResultHistory
import react.setState

external interface HomePageProps : RProps {
    var history: RouteResultHistory
}
external interface HomePageState : RState {
    var searchParams: SearchParams?
    var tags: Map<Boolean, Set<MapTag>>?
    var shiftHeld: Boolean?
    var altHeld: Boolean?
}

val mapFilters = listOf<FilterInfo<SearchParams>>(
    FilterInfo("bot", "AI", FilterCategory.GENERAL) { it.automapper == true },
    FilterInfo("ranked", "Ranked", FilterCategory.GENERAL) { it.ranked == true },
    FilterInfo("curated", "Curated", FilterCategory.GENERAL) { it.curated == true },
    FilterInfo("verified", "Verified Mapper", FilterCategory.GENERAL) { it.verified == true },
    FilterInfo("fs", "Full Spread", FilterCategory.GENERAL) { it.fullSpread == true },

    FilterInfo("chroma", "Chroma", FilterCategory.REQUIREMENTS) { it.chroma == true },
    FilterInfo("noodle", "Noodle", FilterCategory.REQUIREMENTS) { it.noodle == true },
    FilterInfo("me", "Mapping Extensions", FilterCategory.REQUIREMENTS) { it.me == true },
    FilterInfo("cinema", "Cinema", FilterCategory.REQUIREMENTS) { it.cinema == true }
)

inline fun <T> T.applyIf(condition: Boolean, block: T.() -> T): T = if (condition) block(this) else this

class HomePage : RComponent<HomePageProps, HomePageState>() {
    private val modalRef = createRef<ModalComponent>()

    override fun componentWillMount() {
        setState {
            searchParams = fromURL()
        }
    }

    override fun componentDidMount() {
        setPageTitle("Home")
        document.addEventListener("keyup", handleShift)
        document.addEventListener("keydown", handleShift)
    }

    override fun componentWillUnmount() {
        document.removeEventListener("keyup", handleShift)
        document.removeEventListener("keydown", handleShift)
    }

    override fun componentWillUpdate(nextProps: HomePageProps, nextState: HomePageState) {
        if (state.searchParams == nextState.searchParams) {
            val fromParams = fromURL()
            if (fromParams != state.searchParams) {
                nextState.searchParams = fromParams
            }
        }
    }

    private val handleShift = { it: Event ->
        setState {
            shiftHeld = (it as? KeyboardEvent)?.shiftKey ?: false
            altHeld = (it as? KeyboardEvent)?.altKey ?: false
        }
    }

    private fun fromURL() = URLSearchParams(window.location.search).let { params ->
        SearchParams(
            params.get("q") ?: "",
            params.get("auto")?.toBoolean(),
            params.get("minNps")?.toFloatOrNull(),
            params.get("maxNps")?.toFloatOrNull(),
            params.get("chroma")?.toBoolean(),
            SearchOrder.fromString(params.get("order")) ?: SearchOrder.Relevance,
            params.get("from"),
            params.get("to"),
            params.get("noodle")?.toBoolean(),
            params.get("ranked")?.toBoolean(),
            params.get("curated")?.toBoolean(),
            params.get("verified")?.toBoolean(),
            params.get("fullSpread")?.toBoolean(),
            params.get("me")?.toBoolean(),
            params.get("cinema")?.toBoolean(),
            mapOf<Boolean, Map<MapTagType, List<String>>>(true to mapOf(), false to mapOf()).plus(
                params.get("tags")?.split(",", "|")?.groupBy { !it.startsWith("!") }?.mapValues {
                    it.value.map { slug -> slug.removePrefix("!") }.groupBy { slug -> MapTag.fromSlug(slug)?.type ?: MapTagType.None }
                } ?: mapOf()
            )
        )
    }

    private fun updateSearchParams(searchParamsLocal: SearchParams?, row: Int?) {
        if (searchParamsLocal == null) return

        val tagStr = searchParamsLocal.tagsQuery()

        val newQuery = listOfNotNull(
            (if (searchParamsLocal.search.isNotBlank()) "q=${encodeURIComponent(searchParamsLocal.search)}" else null),
            (if (searchParamsLocal.chroma == true) "chroma=true" else null),
            (if (searchParamsLocal.ranked == true) "ranked=true" else null),
            (if (searchParamsLocal.curated == true) "curated=true" else null),
            (if (searchParamsLocal.verified == true) "verified=true" else null),
            (if (searchParamsLocal.noodle == true) "noodle=true" else null),
            (if (searchParamsLocal.me == true) "me=true" else null),
            (if (searchParamsLocal.cinema == true) "cinema=true" else null),
            (if (searchParamsLocal.automapper == true) "auto=true" else null),
            (if (searchParamsLocal.fullSpread == true) "fullSpread=true" else null),
            (if (searchParamsLocal.maxNps != null) "maxNps=${searchParamsLocal.maxNps}" else null),
            (if (searchParamsLocal.minNps != null) "minNps=${searchParamsLocal.minNps}" else null),
            (if (searchParamsLocal.sortOrder != SearchOrder.Relevance) "order=${searchParamsLocal.sortOrder}" else null),
            (if (searchParamsLocal.from != null) "from=${searchParamsLocal.from}" else null),
            (if (searchParamsLocal.to != null) "to=${searchParamsLocal.to}" else null),
            (if (tagStr.isNotEmpty()) "tags=$tagStr" else null)
        )
        val hash = row?.let { "#$it" } ?: ""
        props.history.push((if (newQuery.isEmpty()) "/" else "?" + newQuery.joinToString("&")) + hash)

        setState {
            searchParams = searchParamsLocal
        }
    }

    override fun RBuilder.render() {
        search<SearchParams> {
            typedState = state.searchParams
            sortOrderTarget = SortOrderTarget.Map
            filters = mapFilters
            maxNps = 16
            paramsFromPage = SearchParamGenerator {
                SearchParams(
                    inputRef.current?.value?.trim() ?: "",
                    if (isFiltered("bot")) true else null,
                    if (state.minNps?.let { it > 0 } == true) state.minNps else null,
                    if (state.maxNps?.let { it < props.maxNps } == true) state.maxNps else null,
                    if (isFiltered("chroma")) true else null,
                    state.order ?: SearchOrder.Relevance,
                    state.startDate?.format(dateFormat),
                    state.endDate?.format(dateFormat),
                    if (isFiltered("noodle")) true else null,
                    if (isFiltered("ranked")) true else null,
                    if (isFiltered("curated")) true else null,
                    if (isFiltered("verified")) true else null,
                    if (isFiltered("fs")) true else null,
                    if (isFiltered("me")) true else null,
                    if (isFiltered("cinema")) true else null,
                    this@HomePage.state.tags?.mapValues { o -> o.value.groupBy { y -> y.type }.mapValues { y -> y.value.map { x -> x.slug } } } ?: mapOf()
                )
            }
            extraFilters = functionComponent {
                div("tags") {
                    h4 {
                        +"Tags"
                    }

                    MapTag.sorted.fold(MapTagType.None) { prev, it ->
                        if (it.type != prev) div("break") {}

                        if (it.type != MapTagType.None) {
                            mapTag {
                                attrs.selected = state.tags?.let { tags -> tags.any { x -> x.value.contains(it) } || tags.isEmpty() } ?: false
                                attrs.excluded = state.tags?.get(false)?.contains(it) == true
                                attrs.tag = it

                                attrs.onClick = { _ ->
                                    (state.tags?.get(state.altHeld != true) ?: setOf()).let { tags ->
                                        val shouldAdd = !tags.contains(it)

                                        val newTags = tags.applyIf(state.shiftHeld != true) {
                                            filterTo(hashSetOf()) { o -> o.type != it.type }
                                        }.applyIf(shouldAdd) {
                                            plus(it)
                                        }.applyIf(state.shiftHeld == true && !shouldAdd) {
                                            minus(it)
                                        }

                                        setState {
                                            this.tags = mapOf(
                                                (state.altHeld != true) to newTags,
                                                (state.altHeld == true) to (state.tags?.get(state.altHeld == true)?.let { x -> x - it } ?: setOf())
                                            )
                                        }
                                        window.asDynamic().getSelection().removeAllRanges() as Unit
                                    }
                                }
                            }
                        }
                        it.type
                    }
                }
            }
            updateUI = { params ->
                state.tags = params?.tags?.mapValues { it.value.flatMap { x -> x.value.mapNotNull { y -> MapTag.fromSlug(y) } }.toSet() }
            }
            filterTexts = {
                (state.tags?.flatMap { y -> y.value.map { z -> (if (y.key) "" else "!") + z.slug } } ?: listOf())
            }
            updateSearchParams = ::updateSearchParams
        }
        modal {
            ref = modalRef
        }
        beatmapTable {
            search = state.searchParams
            modal = modalRef
            history = props.history
            updateScrollIndex = {
                updateSearchParams(state.searchParams, if (it < 2) null else it)
            }
        }
    }
}
