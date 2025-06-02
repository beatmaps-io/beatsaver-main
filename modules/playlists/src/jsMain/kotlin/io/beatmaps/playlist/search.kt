package io.beatmaps.playlist

import external.Axios
import external.Moment
import external.dates
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.UserDetail
import io.beatmaps.common.EnvironmentSet
import io.beatmaps.common.MapTagSet
import io.beatmaps.common.SearchParamsPlaylist
import io.beatmaps.common.SearchPlaylistConfig
import io.beatmaps.common.SortOrderTarget
import io.beatmaps.common.api.RankedFilter
import io.beatmaps.maps.collab.collaboratorCard
import io.beatmaps.maps.collab.userSearch
import io.beatmaps.shared.form.multipleChoice
import io.beatmaps.shared.form.slider
import io.beatmaps.shared.form.toggle
import io.beatmaps.shared.loadingElem
import io.beatmaps.shared.search.environments
import io.beatmaps.shared.search.presets
import io.beatmaps.shared.search.sort
import io.beatmaps.shared.search.tags
import io.beatmaps.util.fcmemo
import js.objects.jso
import kotlinx.datetime.Instant
import react.Props
import react.StateSetter
import react.Suspense
import react.createElement
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h4
import react.dom.html.ReactHTML.hr
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.option
import react.dom.html.ReactHTML.select
import react.useCallback
import react.useEffect
import react.useRef
import react.useState
import web.cssom.ClassName
import web.html.HTMLInputElement
import web.html.InputType

external interface PSEProps : Props {
    var config: SearchPlaylistConfig
    var loading: Boolean
    var callback: (SearchPlaylistConfig) -> Unit
}

val mapCounts = listOf(10, 20, 50, 100, 200, 500, 1000)

interface PlaylistSearchFilter<out T> {
    val id: String
}

class PlaylistSearchBooleanFilter(override val id: String, val text: String, val filter: (SearchParamsPlaylist) -> Boolean?) : PlaylistSearchFilter<Boolean?> {
    val ref = useRef<HTMLInputElement>()
}

class PlaylistSearchMultipleChoiceFilter<T : Any?>(override val id: String, val choices: Map<String, T>, val getter: T, val setter: StateSetter<T>) : PlaylistSearchFilter<T>

val playlistSearchEditor = fcmemo<PSEProps>("playlistSearchEditor") { props ->
    val (automapper, setAutomapper) = useState(props.config.searchParams.automapper)
    val (minNps, setMinNps) = useState(props.config.searchParams.minNps ?: 0f)
    val (maxNps, setMaxNps) = useState(props.config.searchParams.maxNps ?: 16f)
    val (startDate, setStartDate) = useState(props.config.searchParams.from?.let { Moment(it.toString()) })
    val (endDate, setEndDate) = useState(props.config.searchParams.to?.let { Moment(it.toString()) })
    val (ranked, setRanked) = useState(props.config.searchParams.ranked)
    val (dateFocused, setDateFocused) = useState<String?>(null)
    val (order, setOrder) = useState(props.config.searchParams.sortOrder)
    val (ascending, setAscending) = useState(props.config.searchParams.ascending)
    val (mapCount, setMapCount) = useState(props.config.mapCount)
    val (tags, setTags) = useState(props.config.searchParams.tags)
    val (environments, setEnvironments) = useState(props.config.searchParams.environments)

    val maxMappersInFilter = 30
    val (currentMappers, setCurrentMappers) = useState(listOf<UserDetail>())

    val filters = listOf<Pair<String, List<PlaylistSearchFilter<Any?>>>>(
        "General" to listOf(
            PlaylistSearchMultipleChoiceFilter(
                "automapper",
                mapOf(
                    "All" to true,
                    "Human" to null,
                    "AI" to false
                ),
                automapper,
                setAutomapper
            ),
            PlaylistSearchMultipleChoiceFilter("ranked", RankedFilter.entries.associateBy { it.name }, ranked, setRanked),
            PlaylistSearchBooleanFilter("curated", "Curated") { it.curated },
            PlaylistSearchBooleanFilter("verified", "Verified Mapper") { it.verified },
            PlaylistSearchBooleanFilter("fullspread", "Full Spread") { it.fullSpread }
        ),
        "Requirements" to listOf(
            PlaylistSearchBooleanFilter("chroma", "Chroma") { it.chroma },
            PlaylistSearchBooleanFilter("noodle", "Noodle Extensions") { it.noodle },
            PlaylistSearchBooleanFilter("me", "Mapping Extensions") { it.me },
            PlaylistSearchBooleanFilter("cinema", "Cinema") { it.cinema },
            PlaylistSearchBooleanFilter("vivify", "Vivify") { it.vivify }
        )
    )

    val searchRef = useRef<HTMLInputElement>()

    fun fromFilter(s: String): Boolean? {
        val filter = filters.flatMap { it.second }.firstOrNull { it.id == s }

        if (filter !is PlaylistSearchBooleanFilter) return null

        return filter.ref.current?.checked.let { b ->
            if (b != true) null else true
        }
    }

    fun doCallback() {
        props.callback(
            SearchPlaylistConfig(
                SearchParamsPlaylist(
                    searchRef.current?.value ?: "",
                    automapper,
                    if (minNps > 0) minNps else null,
                    if (maxNps < 16) maxNps else null,
                    fromFilter("chroma"),
                    order,
                    ascending,
                    startDate?.let { Instant.parse(it.toISOString()) },
                    endDate?.let { Instant.parse(it.toISOString()) },
                    fromFilter("noodle"),
                    ranked,
                    fromFilter("curated"),
                    fromFilter("verified"),
                    fromFilter("fullspread"),
                    fromFilter("me"),
                    fromFilter("cinema"),
                    fromFilter("vivify"),
                    tags,
                    currentMappers.map { it.id },
                    environments
                ),
                mapCount
            )
        )
    }

    useEffect(automapper, minNps, maxNps, startDate, endDate, ranked, order, mapCount, tags, currentMappers, environments) {
        doCallback()
    }

    useEffect(props.config) {
        val mappers = props.config.searchParams.mappers
        if (mappers.isEmpty()) return@useEffect

        Axios.get<List<UserDetail>>(
            "${Config.apibase}/users/ids/${mappers.joinToString(",")}",
            generateConfig<String, List<UserDetail>>()
        ).then {
            setCurrentMappers(it.data)
        }
    }

    div {
        className = ClassName("row")
        div {
            className = ClassName("col-4")
            div {
                className = ClassName("mb-3")
                label {
                    className = ClassName("form-label")
                    htmlFor = "search"
                    +"Search"
                }
                input {
                    type = InputType.text
                    className = ClassName("form-control")
                    key = "search"
                    ref = searchRef
                    defaultValue = props.config.searchParams.search
                    id = "search"
                    placeholder = "Search Terms"
                    disabled = props.loading
                    onChange = {
                        doCallback()
                    }
                }
            }
            div {
                className = ClassName("mb-3")
                Suspense {
                    fallback = loadingElem
                    label {
                        className = ClassName("form-label")
                        +"Date range"
                    }
                    div {
                        dates.dateRangePicker {
                            this.startDate = startDate
                            this.endDate = endDate
                            startDateId = "startobj"
                            endDateId = "endobj"
                            onFocusChange = useCallback {
                                setDateFocused(it)
                            }
                            onDatesChange = useCallback {
                                setStartDate(it.startDate)
                                setEndDate(it.endDate)
                            }
                            isOutsideRange = { false }
                            focusedInput = dateFocused
                            displayFormat = "DD/MM/YYYY"
                            small = true
                            numberOfMonths = 1
                            renderCalendarInfo = useCallback {
                                createElement(
                                    presets,
                                    jso {
                                        callback = { sd, ed ->
                                            setStartDate(sd)
                                            setEndDate(ed)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            slider {
                text = "NPS"
                currentMin = minNps
                currentMax = maxNps
                block = {
                    setMinNps(it[0] / 10f)
                    setMaxNps(it[1] / 10f)
                }
                className = ClassName("mb-3")
            }
            div {
                className = ClassName("mb-3")
                label {
                    className = ClassName("form-label")
                    htmlFor = "sort-by"
                    +"Sort by"
                }
                sort {
                    target = SortOrderTarget.Map
                    cb = { order, asc ->
                        setOrder(order)
                        setAscending(asc)
                    }
                    default = order
                    defaultAsc = ascending
                    id = "sort-by"
                }
            }
            div {
                className = ClassName("mb-3")
                label {
                    className = ClassName("form-label")
                    htmlFor = "map-count"
                    +"Map Count"
                }
                select {
                    className = ClassName("form-select")
                    id = "map-count"
                    disabled = props.loading
                    onChange = { ev ->
                        setMapCount(ev.target.value.toIntOrNull() ?: 100)
                    }
                    value = mapCount.toString()

                    mapCounts.forEach {
                        option {
                            value = it.toString()
                            +it.toString()
                        }
                    }
                }
            }
        }
        div {
            className = ClassName("col-4 mb-3 ps-filter")
            filters.forEachIndexed { idx, s ->
                h4 {
                    className = if (idx > 0) ClassName("mt-4") else null
                    +s.first
                }

                s.second.forEach { f ->
                    if (f is PlaylistSearchBooleanFilter) {
                        toggle {
                            id = f.id
                            disabled = props.loading
                            default = f.filter(props.config.searchParams)
                            text = f.text
                            toggleRef = f.ref
                            block = {
                                doCallback()
                            }
                        }
                    } else if (f is PlaylistSearchMultipleChoiceFilter) {
                        multipleChoice {
                            choices = f.choices
                            name = f.id
                            selectedValue = f.getter
                            block = {
                                f.setter(it)
                            }
                        }
                    }
                }
            }
        }
        div {
            className = ClassName("col-4 mb-3")
            tags {
                default = props.config.searchParams.tags
                callback = useCallback { it: MapTagSet ->
                    setTags(it)
                }
            }
            environments {
                default = props.config.searchParams.environments
                callback = useCallback { it: EnvironmentSet ->
                    setEnvironments(it)
                }
            }
        }
    }
    hr {}
    div {
        className = ClassName("row playlist-mappers")
        label {
            className = ClassName("form-label")
            htmlFor = "mappers"
            +"Mappers"
        }

        div {
            className = ClassName("collaborator-cards")
            currentMappers.forEach { user ->
                collaboratorCard {
                    key = user.id.toString()
                    this.user = user
                    callback = {
                        setCurrentMappers(currentMappers.minus(user))
                    }
                }
            }
        }

        if (currentMappers.size < maxMappersInFilter) {
            userSearch {
                disabled = props.loading
                noForm = true
                excludeUsers = currentMappers.map { it.id }
                callback = { newUser ->
                    setCurrentMappers(currentMappers.plus(newUser))
                }
                addText = "Add"
            }
        }
    }
}
