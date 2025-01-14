package io.beatmaps.index

import io.beatmaps.Config.dateFormat
import io.beatmaps.History
import io.beatmaps.common.EnvironmentSet
import io.beatmaps.common.MapTagSet
import io.beatmaps.common.SearchOrder
import io.beatmaps.common.SortOrderTarget
import io.beatmaps.common.api.RankedFilter
import io.beatmaps.common.toEnvironmentSet
import io.beatmaps.common.toQuery
import io.beatmaps.common.toTagSet
import io.beatmaps.globalContext
import io.beatmaps.setPageTitle
import io.beatmaps.shared.ModalCallbacks
import io.beatmaps.shared.modal
import io.beatmaps.shared.modalContext
import io.beatmaps.shared.search.BooleanFilterInfo
import io.beatmaps.shared.search.ExtraContentRenderer
import io.beatmaps.shared.search.FilterCategory
import io.beatmaps.shared.search.FilterInfo
import io.beatmaps.shared.search.MultipleChoiceFilterInfo
import io.beatmaps.shared.search.SearchParamGenerator
import io.beatmaps.shared.search.environments
import io.beatmaps.shared.search.generateSearchComponent
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
import react.router.useLocation
import react.router.useNavigate
import react.useContext
import react.useEffect
import react.useEffectOnce
import react.useRef
import react.useState

val homePage = fc<Props>("homePage") {
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
            RankedFilter.fromString(params.get("ranked")),
            params.get("curated")?.toBoolean(),
            params.get("verified")?.toBoolean(),
            params.get("followed")?.toBoolean(),
            params.get("fullSpread")?.toBoolean(),
            params.get("me")?.toBoolean(),
            params.get("cinema")?.toBoolean(),
            params.get("tags")?.toQuery()?.toTagSet() ?: mapOf(),
            params.get("environments").toEnvironmentSet()
        )
    }

    useEffectOnce {
        setPageTitle("Home")
    }

    val (tags, setTags) = useState<MapTagSet?>(null)
    val (environments, setEnvironments) = useState<EnvironmentSet?>(null)
    val (searchParams, setSearchParams) = useState(fromURL())
    val usiRef = useRef<(Int) -> Unit>()

    val modalRef = useRef<ModalCallbacks>()
    val history = History(useNavigate())

    val userData = useContext(globalContext)

    val mapFilters = listOfNotNull<FilterInfo<SearchParams, *>>(
        MultipleChoiceFilterInfo(
            "bot",
            "AI",
            FilterCategory.GENERAL,
            mapOf(
                "All" to true,
                "Human" to null,
                "AI" to false
            ),
            null
        ) { it.automapper },
        MultipleChoiceFilterInfo("ranked", "Ranked", FilterCategory.GENERAL, RankedFilter.entries.associateBy { it.name }, RankedFilter.All) { it.ranked },
        BooleanFilterInfo("curated", "Curated", FilterCategory.GENERAL) { it.curated == true },
        BooleanFilterInfo("verified", "Verified Mapper", FilterCategory.GENERAL) { it.verified == true },
        if (userData != null) BooleanFilterInfo("followed", "From Mappers You Follow ", FilterCategory.GENERAL) { it.followed == true } else null,
        BooleanFilterInfo("fs", "Full Spread", FilterCategory.GENERAL) { it.fullSpread == true },

        BooleanFilterInfo("chroma", "Chroma", FilterCategory.REQUIREMENTS) { it.chroma == true },
        BooleanFilterInfo("noodle", "Noodle Extensions", FilterCategory.REQUIREMENTS) { it.noodle == true },
        BooleanFilterInfo("me", "Mapping Extensions", FilterCategory.REQUIREMENTS) { it.me == true },
        BooleanFilterInfo("cinema", "Cinema", FilterCategory.REQUIREMENTS) { it.cinema == true }
    )

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
                    includeIfNotNull(followed, "followed"),
                    (if (tagStr.isNotEmpty()) "tags=$tagStr" else null),
                    (if (environments?.isNotEmpty() == true) "environments=${environments.joinToString(",")}" else null)
                ),
                "", row, searchParams, history
            )
        }

        setSearchParams(searchParamsLocal)
    }

    useEffect(location.search) {
        val newParams = fromURL()
        if (newParams != searchParams) setSearchParams(newParams)
    }

    usiRef.current = { idx ->
        updateSearchParams(searchParams, if (idx < 2) null else idx)
    }

    modal {
        attrs.callbacks = modalRef
    }

    modalContext.Provider {
        attrs.value = modalRef

        beatmapSearch {
            attrs.typedState = searchParams
            attrs.sortOrderTarget = SortOrderTarget.Map
            attrs.filters = mapFilters
            attrs.maxNps = 16
            attrs.paramsFromPage = SearchParamGenerator {
                SearchParams(
                    searchText(),
                    filterOrNull("bot") as? Boolean?,
                    if (minNps > 0) minNps else null,
                    if (maxNps < 16) maxNps else null,
                    if (isFiltered("chroma")) true else null,
                    order,
                    startDate?.format(dateFormat),
                    endDate?.format(dateFormat),
                    if (isFiltered("noodle")) true else null,
                    if (isFiltered("ranked")) filterOrNull("ranked") as? RankedFilter else null,
                    if (isFiltered("curated")) true else null,
                    if (isFiltered("verified")) true else null,
                    if (userData != null && isFiltered("followed")) true else null,
                    if (isFiltered("fs")) true else null,
                    if (isFiltered("me")) true else null,
                    if (isFiltered("cinema")) true else null,
                    tags ?: mapOf(),
                    environments ?: emptySet()
                )
            }
            attrs.extraFilters = ExtraContentRenderer {
                tags {
                    attrs.default = tags
                    attrs.callback = {
                        setTags(it)
                    }
                }
                environments {
                    attrs.default = environments
                    attrs.callback = {
                        setEnvironments(it)
                    }
                }
            }
            attrs.updateUI = { params ->
                setTags(params?.tags)
                setEnvironments(params?.environments)
            }
            attrs.filterTexts = {
                (tags?.flatMap { y -> y.value.map { z -> (if (y.key) "" else "!") + z.slug } } ?: listOf()) +
                    (environments?.map { e -> e.human() } ?: listOf())
            }
            attrs.updateSearchParams = ::updateSearchParams
        }

        beatmapTable {
            attrs.search = searchParams
            attrs.updateScrollIndex = usiRef
        }

        if (userData != null) {
            div("position-fixed btn-group") {
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

val beatmapSearch = generateSearchComponent<SearchParams>("beatmap")
