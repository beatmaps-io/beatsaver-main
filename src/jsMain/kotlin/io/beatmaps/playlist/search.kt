package io.beatmaps.playlist

import external.Axios
import external.DateRangePicker
import external.Moment
import external.generateConfig
import external.reactFor
import io.beatmaps.Config
import io.beatmaps.api.UserDetail
import io.beatmaps.common.SearchParamsPlaylist
import io.beatmaps.common.SearchPlaylistConfig
import io.beatmaps.common.SortOrderTarget
import io.beatmaps.common.api.RankedFilter
import io.beatmaps.maps.collaboratorCard
import io.beatmaps.maps.userSearch
import io.beatmaps.shared.form.multipleChoice
import io.beatmaps.shared.form.slider
import io.beatmaps.shared.form.toggle
import io.beatmaps.shared.search.environments
import io.beatmaps.shared.search.presets
import io.beatmaps.shared.search.sort
import io.beatmaps.shared.search.tags
import kotlinx.datetime.Instant
import kotlinx.html.InputType
import kotlinx.html.id
import kotlinx.html.js.onChangeFunction
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import react.Props
import react.StateSetter
import react.createElement
import react.dom.attrs
import react.dom.defaultValue
import react.dom.div
import react.dom.h4
import react.dom.hr
import react.dom.input
import react.dom.label
import react.dom.option
import react.dom.select
import react.fc
import react.useEffect
import react.useRef
import react.useState

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

val playlistSearchEditor = fc<PSEProps> { props ->
    val (automapper, setAutomapper) = useState(props.config.searchParams.automapper)
    val (minNps, setMinNps) = useState(props.config.searchParams.minNps ?: 0f)
    val (maxNps, setMaxNps) = useState(props.config.searchParams.maxNps ?: 16f)
    val (startDate, setStartDate) = useState(props.config.searchParams.from?.let { Moment(it.toString()) })
    val (endDate, setEndDate) = useState(props.config.searchParams.to?.let { Moment(it.toString()) })
    val (ranked, setRanked) = useState(props.config.searchParams.ranked)
    val (dateFocused, setDateFocused) = useState<String?>(null)
    val (order, setOrder) = useState(props.config.searchParams.sortOrder)
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
            PlaylistSearchBooleanFilter("cinema", "Cinema") { it.cinema }
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
                    startDate?.let { Instant.parse(it.toISOString()) },
                    endDate?.let { Instant.parse(it.toISOString()) },
                    fromFilter("noodle"),
                    ranked,
                    fromFilter("curated"),
                    fromFilter("verified"),
                    fromFilter("fullspread"),
                    fromFilter("me"),
                    fromFilter("cinema"),
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

    div("row") {
        div("col-4") {
            div("mb-3") {
                label("form-label") {
                    attrs.reactFor = "search"
                    +"Search"
                }
                input(type = InputType.text, classes = "form-control") {
                    key = "search"
                    ref = searchRef
                    attrs.defaultValue = props.config.searchParams.search
                    attrs.id = "search"
                    attrs.placeholder = "Search Terms"
                    attrs.disabled = props.loading
                    attrs.onChangeFunction = {
                        doCallback()
                    }
                }
            }
            div("mb-3") {
                label("form-label") {
                    +"Date range"
                }
                div {
                    DateRangePicker.default {
                        attrs.startDate = startDate
                        attrs.endDate = endDate
                        attrs.startDateId = "startobj"
                        attrs.endDateId = "endobj"
                        attrs.onFocusChange = {
                            setDateFocused(it)
                        }
                        attrs.onDatesChange = {
                            setStartDate(it.startDate)
                            setEndDate(it.endDate)
                        }
                        attrs.isOutsideRange = { false }
                        attrs.focusedInput = dateFocused
                        attrs.displayFormat = "DD/MM/YYYY"
                        attrs.small = true
                        attrs.numberOfMonths = 1
                        attrs.renderCalendarInfo = {
                            createElement<Props> {
                                presets {
                                    attrs.callback = { sd, ed ->
                                        setStartDate(sd)
                                        setEndDate(ed)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            slider {
                attrs.text = "NPS"
                attrs.currentMin = minNps
                attrs.currentMax = maxNps
                attrs.block = {
                    setMinNps(it[0] / 10f)
                    setMaxNps(it[1] / 10f)
                }
                attrs.className = "mb-3"
            }
            div("mb-3") {
                label("form-label") {
                    attrs.reactFor = "sort-by"
                    +"Sort by"
                }
                sort {
                    attrs.target = SortOrderTarget.Map
                    attrs.cb = {
                        setOrder(it)
                    }
                    attrs.default = order
                    attrs.id = "sort-by"
                }
            }
            div("mb-3") {
                label("form-label") {
                    attrs.reactFor = "map-count"
                    +"Map Count"
                }
                select("form-select") {
                    attrs {
                        id = "map-count"
                        disabled = props.loading
                        onChangeFunction = { ev ->
                            setMapCount((ev.target as HTMLSelectElement).value.toIntOrNull() ?: 100)
                        }
                    }
                    mapCounts.forEach {
                        option {
                            attrs.value = it.toString()
                            attrs.selected = it == mapCount
                            +it.toString()
                        }
                    }
                }
            }
        }
        div("col-4 mb-3 ps-filter") {
            filters.forEachIndexed { idx, s ->
                h4(if (idx > 0) "mt-4" else "") {
                    +s.first
                }

                s.second.forEach { f ->
                    if (f is PlaylistSearchBooleanFilter) {
                        toggle {
                            attrs.id = f.id
                            attrs.disabled = props.loading
                            attrs.default = f.filter(props.config.searchParams)
                            attrs.text = f.text
                            attrs.ref = f.ref
                            attrs.block = {
                                doCallback()
                            }
                        }
                    } else if (f is PlaylistSearchMultipleChoiceFilter) {
                        multipleChoice {
                            attrs.choices = f.choices
                            attrs.name = f.id
                            attrs.selectedValue = f.getter
                            attrs.block = {
                                f.setter(it)
                            }
                        }
                    }
                }
            }
        }
        div("col-4 mb-3") {
            tags {
                attrs.default = props.config.searchParams.tags
                attrs.callback = {
                    setTags(it)
                }
            }
            environments {
                attrs.default = props.config.searchParams.environments
                attrs.callback = {
                    setEnvironments(it)
                }
            }
        }
    }
    hr {}
    div("row playlist-mappers") {
        label("form-label") {
            attrs.reactFor = "mappers"
            +"Mappers"
        }

        div("collaborator-cards") {
            currentMappers.forEach { user ->
                collaboratorCard {
                    key = user.id.toString()
                    attrs.user = user
                    attrs.callback = {
                        setCurrentMappers(currentMappers.minus(user))
                    }
                }
            }
        }

        if (currentMappers.size < maxMappersInFilter) {
            userSearch {
                attrs.disabled = props.loading
                attrs.excludeUsers = currentMappers.map { it.id }
                attrs.callback = { newUser ->
                    setCurrentMappers(currentMappers.plus(newUser))
                }
                attrs.addText = "Add"
            }
        }
    }
}
