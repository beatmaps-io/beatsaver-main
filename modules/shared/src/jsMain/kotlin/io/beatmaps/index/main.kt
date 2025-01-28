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
import io.beatmaps.util.fcmemo
import io.beatmaps.util.toPlaylistConfig
import js.objects.jso
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.router.useLocation
import react.router.useNavigate
import react.use
import react.useCallback
import react.useEffect
import react.useEffectOnce
import react.useMemo
import react.useRef
import react.useState
import web.cssom.ClassName
import web.cssom.Position
import web.cssom.px
import web.html.ButtonType
import web.url.URLSearchParams

val homePage = fcmemo<Props>("homePage") {
    val location = useLocation()

    fun fromURL() = URLSearchParams(location.search).let { params ->
        SearchParams(
            params["q"] ?: "",
            params["auto"]?.toBoolean(),
            params["minNps"]?.toFloatOrNull(),
            params["maxNps"]?.toFloatOrNull(),
            params["chroma"]?.toBoolean(),
            SearchOrder.fromString(params["order"]) ?: SearchOrder.Relevance,
            params["from"],
            params["to"],
            params["noodle"]?.toBoolean(),
            RankedFilter.fromString(params["ranked"]),
            params["curated"]?.toBoolean(),
            params["verified"]?.toBoolean(),
            params["followed"]?.toBoolean(),
            params["fullSpread"]?.toBoolean(),
            params["me"]?.toBoolean(),
            params["cinema"]?.toBoolean(),
            params["vivify"]?.toBoolean(),
            params["tags"]?.toQuery()?.toTagSet() ?: mapOf(),
            params["environments"].toEnvironmentSet()
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

    val userData = use(globalContext)

    val mapFilters = useMemo(userData) {
        listOfNotNull<FilterInfo<SearchParams, *>>(
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
            BooleanFilterInfo("cinema", "Cinema", FilterCategory.REQUIREMENTS) { it.cinema == true },
            BooleanFilterInfo("vivify", "Vivify", FilterCategory.REQUIREMENTS) { it.vivify == true }
        )
    }

    val updateSearchParams = useCallback(searchParams) { searchParamsLocal: SearchParams?, row: Int? ->
        if (searchParamsLocal == null) return@useCallback

        with(searchParamsLocal) {
            buildURL(queryParams().toList(), "", row, searchParams, history)
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
        callbacks = modalRef
    }

    val paramGenerator = useMemo(tags, environments) {
        SearchParamGenerator {
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
                if (isFiltered("vivify")) true else null,
                tags ?: mapOf(),
                environments ?: emptySet()
            )
        }
    }

    val extraFilters = useMemo(tags, environments) {
        ExtraContentRenderer {
            tags {
                default = tags
                callback = {
                    setTags(it)
                }
            }
            environments {
                default = environments
                callback = {
                    setEnvironments(it)
                }
            }
        }
    }

    val filterTextCallback = useCallback(tags, environments) {
        (tags?.flatMap { y -> y.value.map { z -> (if (y.key) "" else "!") + z.slug } } ?: listOf()) +
            (environments?.map { e -> e.human() } ?: listOf())
    }

    modalContext.Provider {
        value = modalRef

        beatmapSearch {
            typedState = searchParams
            sortOrderTarget = SortOrderTarget.Map
            filters = mapFilters
            maxNps = 16
            paramsFromPage = paramGenerator
            this.extraFilters = extraFilters
            updateUI = useCallback { params: SearchParams? ->
                setTags(params?.tags)
                setEnvironments(params?.environments)
            }
            filterTexts = filterTextCallback
            this.updateSearchParams = updateSearchParams
        }

        beatmapTable {
            search = searchParams
            updateScrollIndex = usiRef
        }

        if (userData != null) {
            div {
                className = ClassName("position-fixed btn-group")
                style = jso {
                    position = Position.absolute
                    right = 10.px
                    bottom = 10.px
                }

                button {
                    type = ButtonType.button
                    className = ClassName("btn btn-sm btn-primary")
                    title = "Create playlist from search"
                    onClick = {
                        it.preventDefault()

                        history.go(
                            "/playlists/new",
                            stateNavOptions(searchParams.toPlaylistConfig(), false)
                        )
                    }
                    i {
                        className = ClassName("fas fa-list-ul")
                    }
                }
            }
        }
    }
}

val beatmapSearch = generateSearchComponent<SearchParams>("beatmap")
