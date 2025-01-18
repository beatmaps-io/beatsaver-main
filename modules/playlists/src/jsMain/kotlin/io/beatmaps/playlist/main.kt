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
import io.beatmaps.shared.search.generateSearchComponent
import io.beatmaps.util.buildURL
import io.beatmaps.util.fcmemo
import io.beatmaps.util.includeIfNotNull
import react.Props
import react.router.useLocation
import react.router.useNavigate
import react.useCallback
import react.useEffect
import react.useEffectOnce
import react.useMemo
import react.useRef
import react.useState
import web.url.URLSearchParams

val playlistFilters = listOf<FilterInfo<PlaylistSearchParams, *>>(
    BooleanFilterInfo("curated", "Curated", FilterCategory.GENERAL) { it.curated == true },
    BooleanFilterInfo("verified", "Verified Mapper", FilterCategory.GENERAL) { it.verified == true }
)

val playlistFeed = fcmemo<Props>("playlistFeed") {
    useEffectOnce {
        setPageTitle("Playlists")
    }

    val location = useLocation()
    val history = History(useNavigate())
    fun fromURL() = URLSearchParams(location.search).let { params ->
        PlaylistSearchParams(
            params["q"] ?: "",
            params["minNps"]?.toFloatOrNull(),
            params["maxNps"]?.toFloatOrNull(),
            params["from"],
            params["to"],
            params["includeEmpty"]?.toBoolean(),
            params["curated"]?.toBoolean(),
            params["verified"]?.toBoolean(),
            SearchOrder.fromString(params.get("order")) ?: SearchOrder.Relevance
        )
    }

    val (searchParams, setSearchParams) = useState(fromURL())
    val usiRef = useRef<(Int) -> Unit>()

    useEffect(location) {
        val newParams = fromURL()
        if (newParams != searchParams) setSearchParams(newParams)
    }

    val updateSearchParams = useCallback(searchParams) { searchParamsLocal: PlaylistSearchParams?, row: Int? ->
        if (searchParamsLocal == null) return@useCallback

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

    usiRef.current = { idx ->
        updateSearchParams(searchParams, if (idx < 2) null else idx)
    }

    val paramGenerator = useMemo {
        SearchParamGenerator {
            PlaylistSearchParams(
                searchText(),
                if (minNps > 0) minNps else null,
                if (maxNps < 16) maxNps else null,
                startDate?.format(dateFormat),
                endDate?.format(dateFormat),
                null,
                if (isFiltered("curated")) true else null,
                if (isFiltered("verified")) true else null,
                order
            )
        }
    }

    playlistSearch {
        typedState = searchParams
        sortOrderTarget = SortOrderTarget.Playlist
        maxNps = 16
        filters = playlistFilters
        paramsFromPage = paramGenerator
        this.updateSearchParams = updateSearchParams
    }
    playlistTable {
        search = searchParams
        updateScrollIndex = usiRef
    }
}

val playlistSearch = generateSearchComponent<PlaylistSearchParams>("playlist")
