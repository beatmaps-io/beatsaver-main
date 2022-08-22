package io.beatmaps.playlist

import external.Moment
import io.beatmaps.api.SearchOrder
import io.beatmaps.api.SortOrderTarget
import io.beatmaps.shared.FilterCategory
import io.beatmaps.shared.FilterInfo
import io.beatmaps.shared.Search
import io.beatmaps.shared.search
import io.beatmaps.shared.toggle
import io.beatmaps.dateFormat
import io.beatmaps.index.encodeURIComponent
import io.beatmaps.setPageTitle
import kotlinx.browser.window
import org.w3c.dom.url.URLSearchParams
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
import react.createRef
import react.dom.div
import react.functionComponent
import react.ref
import react.router.dom.RouteResultHistory
import react.setState

external interface PlaylistFeedProps : RProps {
    var history: RouteResultHistory
}

external interface PlaylistFeedState : RState {
    var searchParams: PlaylistSearchParams
}

val playlistFilters = listOf<FilterInfo<PlaylistSearchParams>>(
    FilterInfo("curated", "Curated", FilterCategory.GENERAL) { it.curated == true },
    FilterInfo("verified", "Verified Mapper", FilterCategory.GENERAL) { it.verified == true }
)

class PlaylistFeed : RComponent<PlaylistFeedProps, PlaylistFeedState>() {
    private val searchRef = createRef<Search<PlaylistSearchParams>>()

    override fun componentWillMount() {
        setState {
            searchParams = fromURL()
        }
    }

    override fun componentDidMount() {
        setPageTitle("Playlists")

        searchRef.current?.updateUI(state.searchParams)
    }

    private fun Search<PlaylistSearchParams>.updateUI(fromParams: PlaylistSearchParams) {
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
        }
    }

    private fun fromURL() = URLSearchParams(window.location.search).let { params ->
        PlaylistSearchParams(
            params.get("q") ?: "",
            params.get("minNps")?.toFloatOrNull(),
            params.get("maxNps")?.toFloatOrNull(),
            params.get("from"),
            params.get("to"),
            null,
            params.get("curated")?.toBoolean(),
            params.get("verified")?.toBoolean(),
            SearchOrder.fromString(params.get("order")) ?: SearchOrder.Relevance
        )
    }

    override fun componentWillUpdate(nextProps: PlaylistFeedProps, nextState: PlaylistFeedState) {
        if (state.searchParams == nextState.searchParams) {
            val fromParams = fromURL()
            if (fromParams != state.searchParams) {
                nextState.searchParams = fromParams
            }
        }
    }

    override fun componentDidUpdate(prevProps: PlaylistFeedProps, prevState: PlaylistFeedState, snapshot: Any) {
        if (prevState.searchParams != state.searchParams) {
            searchRef.current?.updateUI(state.searchParams)
        }
    }

    private fun updateSearchParams(searchParamsLocal: PlaylistSearchParams, row: Int?) {
        val newQuery = listOfNotNull(
            (if (searchParamsLocal.search.isNotBlank()) "q=${encodeURIComponent(searchParamsLocal.search)}" else null),
            (if (searchParamsLocal.curated == true) "curated=true" else null),
            (if (searchParamsLocal.verified == true) "verified=true" else null),
            (if (searchParamsLocal.maxNps != null) "maxNps=${searchParamsLocal.maxNps}" else null),
            (if (searchParamsLocal.minNps != null) "minNps=${searchParamsLocal.minNps}" else null),
            (if (searchParamsLocal.sortOrder != SearchOrder.Relevance) "order=${searchParamsLocal.sortOrder}" else null),
            (if (searchParamsLocal.from != null) "from=${searchParamsLocal.from}" else null),
            (if (searchParamsLocal.to != null) "to=${searchParamsLocal.to}" else null)
        )
        val hash = row?.let { "#$it" } ?: ""
        props.history.push((if (newQuery.isEmpty()) "/playlists" else "?" + newQuery.joinToString("&")) + hash)

        setState {
            searchParams = searchParamsLocal
        }
    }

    override fun RBuilder.render() {
        search<PlaylistSearchParams> {
            ref = searchRef
            sortOrderTarget = SortOrderTarget.Playlist
            maxNps = 16
            filters = playlistFilters
            filterBody = functionComponent { props ->
                div {
                    props.filterRefs.entries.forEach { filter ->
                        toggle(filter.key.key, filter.key.name, filter.value) {
                            props.setState {
                                state.filterMap[filter.key] = it
                            }
                        }
                    }
                }
            }
            getSearchParams = {
                PlaylistSearchParams(
                    inputRef.current?.value?.trim() ?: "",
                    if (state.minNps > 0) state.minNps else null,
                    if (state.maxNps < props.maxNps) state.maxNps else null,
                    state.startDate?.format(dateFormat),
                    state.endDate?.format(dateFormat),
                    null,
                    if (isFiltered("curated")) true else null,
                    if (isFiltered("verified")) true else null,
                    state.order
                )
            }
            updateSearchParams = {
                updateSearchParams(it, null)
            }
        }
        playlistTable {
            search = state.searchParams
            own = false
            history = props.history
            visible = true
            updateScrollIndex = {
                updateSearchParams(state.searchParams, if (it < 2) null else it)
            }
        }
    }
}

fun RBuilder.playlistFeed(handler: PlaylistFeedProps.() -> Unit): ReactElement {
    return child(PlaylistFeed::class) {
        this.attrs(handler)
    }
}
