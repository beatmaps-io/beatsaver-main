package io.beatmaps.index

import external.Moment
import io.beatmaps.api.SearchOrder
import io.beatmaps.shared.FilterCategory
import io.beatmaps.shared.FilterInfo
import io.beatmaps.common.MapTag
import io.beatmaps.common.MapTagType
import io.beatmaps.shared.Search
import io.beatmaps.shared.search
import io.beatmaps.shared.toggle
import io.beatmaps.dateFormat
import io.beatmaps.maps.mapTag
import io.beatmaps.setPageTitle
import kotlinx.browser.window
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
    var searchParams: SearchParams
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

class HomePage : RComponent<HomePageProps, HomePageState>() {
    private val searchRef = createRef<Search<SearchParams>>()
    private val modalRef = createRef<ModalComponent>()

    override fun componentWillMount() {
        setState {
            searchParams = fromURL()
        }
    }

    override fun componentDidMount() {
        setPageTitle("Home")

        searchRef.current?.updateUI(state.searchParams)
    }

    private fun Search<SearchParams>.updateUI(fromParams: SearchParams) {
        inputRef.current?.value = fromParams.search
        sortRef.current?.selectedIndex = fromParams.sortOrder.idx
        setState {
            filterRefs.forEach {
                val newState = it.key.fromParams(fromParams)
                it.value.current?.checked = newState
                filterMap[it.key] = newState
            }

            minNps = fromParams.minNps ?: 0f
            maxNps = fromParams.maxNps ?: props.maxNps.toFloat()
            order = fromParams.sortOrder
            startDate = fromParams.from?.let { Moment(it) }
            endDate = fromParams.to?.let { Moment(it) }
            tags = fromParams.tags.mapValues { it.value.flatMap { x -> x.value.mapNotNull { y -> MapTag.fromSlug(y) } }.toSet() }
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

    override fun componentWillUpdate(nextProps: HomePageProps, nextState: HomePageState) {
        if (state.searchParams == nextState.searchParams) {
            val fromParams = fromURL()
            if (fromParams != state.searchParams) {
                nextState.searchParams = fromParams
            }
        }
    }

    override fun componentDidUpdate(prevProps: HomePageProps, prevState: HomePageState, snapshot: Any) {
        if (prevState.searchParams != state.searchParams) {
            searchRef.current?.updateUI(state.searchParams)
        }
    }

    private fun updateSearchParams(searchParamsLocal: SearchParams, row: Int?) {
        val tagStr = searchParamsLocal.tags.flatMap { x ->
            x.value.map { y ->
                y.value.joinToString(if (x.key) "|" else ",") {
                    (if (x.key) "" else "!") + it
                }
            }
        }.joinToString(",")

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
            ref = searchRef
            filters = mapFilters
            filterBody = functionComponent { props ->
                div("d-flex") {
                    div {
                        props.filterRefs.entries.fold(FilterCategory.NONE) { cat, filter ->
                            if (cat != filter.key.cat) {
                                h4(if (cat == FilterCategory.NONE) "" else "mt-4") {
                                    +filter.key.cat.toString()
                                }
                            }

                            toggle(filter.key.key, filter.key.name, filter.value) {
                                props.setState {
                                    state.filterMap[filter.key] = it
                                }
                            }

                            filter.key.cat
                        }
                    }

                    div("tags") {
                        h4 {
                            +"Tags"
                        }

                        MapTag.sorted.fold(MapTagType.None) { prev, it ->
                            if (it.type != prev) div("break") {}

                            if (it.type != MapTagType.None) {
                                mapTag {
                                    attrs.selected = props.tags?.let { tags -> tags.any { x -> x.value.contains(it) } || tags.isEmpty() } ?: false
                                    attrs.excluded = props.tags?.get(false)?.contains(it) == true
                                    attrs.tag = it

                                    attrs.onClick = { _ ->
                                        props.modifyTags(it)
                                    }
                                }
                            }
                            it.type
                        }
                    }
                }
            }
            maxNps = 16
            getSearchParams = {
                SearchParams(
                    inputRef.current?.value?.trim() ?: "",
                    if (isFiltered("bot")) true else null,
                    if (state.minNps > 0) state.minNps else null,
                    if (state.maxNps < props.maxNps) state.maxNps else null,
                    if (isFiltered("chroma")) true else null,
                    state.order,
                    state.startDate?.format(dateFormat),
                    state.endDate?.format(dateFormat),
                    if (isFiltered("noodle")) true else null,
                    if (isFiltered("ranked")) true else null,
                    if (isFiltered("curated")) true else null,
                    if (isFiltered("verified")) true else null,
                    if (isFiltered("fs")) true else null,
                    if (isFiltered("me")) true else null,
                    if (isFiltered("cinema")) true else null,
                    state.tags?.mapValues { o -> o.value.groupBy { y -> y.type }.mapValues { y -> y.value.map { x -> x.slug } } } ?: mapOf()
                )
            }
            updateSearchParams = {
                updateSearchParams(it, null)
            }
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
