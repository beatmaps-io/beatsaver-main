package io.beatmaps.index

import io.beatmaps.History
import io.beatmaps.common.MapTagSet
import io.beatmaps.common.SearchOrder
import io.beatmaps.common.SortOrderTarget
import io.beatmaps.common.toQuery
import io.beatmaps.common.toTagSet
import io.beatmaps.dateFormat
import io.beatmaps.globalContext
import io.beatmaps.shared.search.ExtraContentRenderer
import io.beatmaps.shared.search.FilterCategory
import io.beatmaps.shared.search.FilterInfo
import io.beatmaps.shared.search.SearchParamGenerator
import io.beatmaps.shared.search.search
import io.beatmaps.shared.search.tags
import io.beatmaps.stateNavOptions
import io.beatmaps.util.buildURL
import io.beatmaps.util.includeIfNotNull
import io.beatmaps.util.toPlaylistConfig
import kotlinx.html.ButtonType
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import org.w3c.dom.url.URLSearchParams
import react.Props
import react.dom.button
import react.dom.div
import react.dom.i
import react.dom.jsStyle
import react.fc
import react.ref
import react.router.useLocation
import react.router.useNavigate
import react.useContext
import react.useEffect
import react.useRef
import react.useState

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

val homePage = fc<Props> {
    val location = useLocation()

    fun fromURL() = URLSearchParams(location.search).let { params ->
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
            params.get("tags")?.toQuery()?.toTagSet() ?: mapOf()
        )
    }

    val (searchParams, setSearchParams) = useState(fromURL())
    val (tags, setTags) = useState<MapTagSet?>(null)

    val modalRef = useRef<ModalComponent>()
    val history = History(useNavigate())

    val userData = useContext(globalContext)

    fun updateSearchParams(searchParamsLocal: SearchParams?, row: Int?) {
        if (searchParamsLocal == null) return

        val tagStr = searchParamsLocal.tags.toQuery()

        with(searchParamsLocal) {
            buildURL(
                listOfNotNull(
                    *queryParams(),
                    includeIfNotNull(chroma, "chroma"),
                    includeIfNotNull(ranked, "ranked"),
                    includeIfNotNull(curated, "curated"),
                    includeIfNotNull(verified, "verified"),
                    includeIfNotNull(noodle, "noodle"),
                    includeIfNotNull(me, "me"),
                    includeIfNotNull(cinema, "cinema"),
                    includeIfNotNull(automapper, "auto"),
                    includeIfNotNull(fullSpread, "fullSpread"),
                    (if (tagStr.isNotEmpty()) "tags=$tagStr" else null)
                ),
                "", row, searchParams, history
            )
        }

        setSearchParams(searchParamsLocal)
    }

    useEffect(location.search) {
        setSearchParams(fromURL())
    }

    modal {
        ref = modalRef
    }

    modalContext.Provider {
        attrs.value = modalRef

        search<SearchParams> {
            typedState = searchParams
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
                    tags ?: mapOf()
                )
            }
            extraFilters = ExtraContentRenderer {
                tags {
                    attrs.default = tags
                    attrs.callback = {
                        setTags(it)
                    }
                }
            }
            updateUI = { params ->
                setTags(params?.tags)
            }
            filterTexts = {
                (tags?.flatMap { y -> y.value.map { z -> (if (y.key) "" else "!") + z.slug } } ?: listOf())
            }
            updateSearchParams = ::updateSearchParams
        }

        beatmapTable {
            attrs.search = searchParams
            attrs.updateScrollIndex = {
                updateSearchParams(searchParams, if (it < 2) null else it)
            }
        }

        if (userData != null) {
            div("position-absolute btn-group") {
                attrs.jsStyle {
                    position = "absolute"
                    right = "10px"
                    bottom = "10px"
                }

                button(type = ButtonType.button, classes = "btn btn-sm btn-primary") {
                    attrs.title = "Create playlist from search"
                    attrs.onClickFunction = {
                        it.preventDefault()

                        history.go(
                            "/playlists/new",
                            stateNavOptions(searchParams.toPlaylistConfig(), false)
                        )
                    }
                    i("fas fa-list-ul") { }
                }
            }
        }
    }
}
