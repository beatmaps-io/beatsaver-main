package io.beatmaps.playlist

import io.beatmaps.Config.dateFormat
import io.beatmaps.History
import io.beatmaps.common.SearchOrder
import io.beatmaps.common.SortOrderTarget
import io.beatmaps.setPageTitle
import io.beatmaps.shared.search.BooleanFilterInfo
import io.beatmaps.shared.search.FilterCategory
import io.beatmaps.shared.search.FilterInfo
import io.beatmaps.shared.search.SearchParamGenerator
import io.beatmaps.shared.search.search
import io.beatmaps.util.buildURL
import io.beatmaps.util.includeIfNotNull
import org.w3c.dom.url.URLSearchParams
import react.Props
import react.fc
import react.router.useLocation
import react.router.useNavigate
import react.useEffect
import react.useEffectOnce
import react.useState

val playlistFilters = listOf<FilterInfo<PlaylistSearchParams, *>>(
    BooleanFilterInfo("curated", "Curated", FilterCategory.GENERAL) { it.curated == true },
    BooleanFilterInfo("verified", "Verified Mapper", FilterCategory.GENERAL) { it.verified == true }
)

val playlistFeed = fc<Props>("playlistFeed") {
    useEffectOnce {
        setPageTitle("Playlists")
    }

    val location = useLocation()
    val history = History(useNavigate())
    fun fromURL() = URLSearchParams(location.search).let { params ->
        PlaylistSearchParams(
            params.get("q") ?: "",
            params.get("minNps")?.toFloatOrNull(),
            params.get("maxNps")?.toFloatOrNull(),
            params.get("from"),
            params.get("to"),
            params.get("includeEmpty")?.toBoolean(),
            params.get("curated")?.toBoolean(),
            params.get("verified")?.toBoolean(),
            SearchOrder.fromString(params.get("order")) ?: SearchOrder.Relevance
        )
    }

    val (searchParams, setSearchParams) = useState(fromURL())

    useEffect(location) {
        val newParams = fromURL()
        if (newParams != searchParams) setSearchParams(newParams)
    }

    fun updateSearchParams(searchParamsLocal: PlaylistSearchParams?, row: Int?) {
        if (searchParamsLocal == null) return

        with(searchParamsLocal) {
            buildURL(
                listOfNotNull(
                    *queryParams(),
                    includeIfNotNull(curated, "curated"),
                    includeIfNotNull(verified, "verified")
                ),
                "playlists", row, searchParams, history
            )
        }

        setSearchParams(searchParamsLocal)
    }

    search<PlaylistSearchParams> {
        typedState = searchParams
        sortOrderTarget = SortOrderTarget.Playlist
        maxNps = 16
        filters = playlistFilters
        paramsFromPage = SearchParamGenerator {
            PlaylistSearchParams(
                inputRef.current?.value?.trim() ?: "",
                if (state.minNps?.let { it > 0 } == true) state.minNps else null,
                if (state.maxNps?.let { it < props.maxNps } == true) state.maxNps else null,
                state.startDate?.format(dateFormat),
                state.endDate?.format(dateFormat),
                null,
                if (isFiltered("curated")) true else null,
                if (isFiltered("verified")) true else null,
                state.order ?: SearchOrder.Relevance
            )
        }
        updateSearchParams = ::updateSearchParams
    }
    playlistTable {
        attrs.search = searchParams
        attrs.updateScrollIndex = {
            updateSearchParams(searchParams, if (it < 2) null else it)
        }
    }
}
