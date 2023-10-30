package io.beatmaps.playlist

import external.Axios
import external.DateRangePicker
import external.Moment
import external.generateConfig
import external.reactFor
import io.beatmaps.Config
import io.beatmaps.api.UserDetail
import io.beatmaps.common.SearchOrder
import io.beatmaps.common.SearchParamsPlaylist
import io.beatmaps.common.SearchPlaylistConfig
import io.beatmaps.common.SortOrderTarget
import io.beatmaps.maps.collaboratorCard
import io.beatmaps.maps.userSearch
import io.beatmaps.shared.presets
import io.beatmaps.shared.slider
import io.beatmaps.shared.tags
import io.beatmaps.shared.toggle
import kotlinx.datetime.Instant
import kotlinx.html.InputType
import kotlinx.html.id
import kotlinx.html.js.onChangeFunction
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import react.Props
import react.createElement
import react.dom.attrs
import react.dom.defaultValue
import react.dom.div
import react.dom.h4
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

val mapCounts = listOf(10, 20, 50, 100, 200, 500)

data class PlaylistSearchBooleanFilter(val id: String, val text: String, val filter: (SearchParamsPlaylist) -> Boolean?) {
    val ref = useRef<HTMLInputElement>()
}

val playlistSearchEditor = fc<PSEProps> { props ->
    val (minNps, setMinNps) = useState(props.config.searchParams.minNps ?: 0f)
    val (maxNps, setMaxNps) = useState(props.config.searchParams.maxNps ?: 16f)
    val (startDate, setStartDate) = useState(props.config.searchParams.from?.let { Moment(it.toString()) })
    val (endDate, setEndDate) = useState(props.config.searchParams.to?.let { Moment(it.toString()) })
    val (dateFocused, setDateFocused) = useState<String?>(null)
    val (order, setOrder) = useState(props.config.searchParams.sortOrder)
    val (mapCount, setMapCount) = useState(props.config.mapCount)
    val (tags, setTags) = useState(props.config.searchParams.tags)

    val maxMappersInFilter = 30
    val (currentMappers, setCurrentMappers) = useState(listOf<UserDetail>())

    val filters = listOf(
        "General" to listOf(
            PlaylistSearchBooleanFilter("automapper", "AI") { it.automapper },
            PlaylistSearchBooleanFilter("ranked", "Ranked") { it.ranked },
            PlaylistSearchBooleanFilter("curated", "Curated") { it.curated },
            PlaylistSearchBooleanFilter("verified", "Verified Mapper") { it.verified },
            PlaylistSearchBooleanFilter("fullspread", "Full Spread") { it.fullSpread }
        ),
        "Requirements" to listOf(
            PlaylistSearchBooleanFilter("chroma", "Chroma") { it.chroma },
            PlaylistSearchBooleanFilter("noodle", "Noodle") { it.noodle },
            PlaylistSearchBooleanFilter("me", "Mapping Extensions") { it.me },
            PlaylistSearchBooleanFilter("cinema", "Cinema") { it.cinema }
        )
    )

    val searchRef = useRef<HTMLInputElement>()

    fun fromFilter(s: String) =
        filters.flatMap { it.second }.firstOrNull { it.id == s }?.ref?.current?.checked.let { b ->
            if (b != true) null else true
        }

    fun doCallback() {
        props.callback(
            SearchPlaylistConfig(
                SearchParamsPlaylist(
                    searchRef.current?.value ?: "",
                    fromFilter("automapper"),
                    if (minNps > 0) minNps else null,
                    if (maxNps < 16) maxNps else null,
                    fromFilter("chroma"),
                    order,
                    startDate?.let { Instant.parse(it.toISOString()) },
                    endDate?.let { Instant.parse(it.toISOString()) },
                    fromFilter("noodle"),
                    fromFilter("ranked"),
                    fromFilter("curated"),
                    fromFilter("verified"),
                    fromFilter("fullspread"),
                    fromFilter("me"),
                    fromFilter("cinema"),
                    tags,
                    currentMappers.map { it.id }
                ),
                mapCount
            )
        )
    }

    useEffect(minNps, maxNps, startDate, endDate, order, mapCount, tags, currentMappers) {
        doCallback()
    }

    useEffect(props.config) {
        Axios.get<List<UserDetail>>(
            "${Config.apibase}/users/ids/${props.config.searchParams.mappers.joinToString(",")}",
            generateConfig<String, List<UserDetail>>()
        ).then {
            setCurrentMappers(it.data)
        }
    }

    div("row") {
        div("col-6") {
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
                select("form-select") {
                    attrs {
                        id = "sort-by"
                        disabled = props.loading
                        onChangeFunction = { ev ->
                            setOrder(SearchOrder.fromString((ev.target as HTMLSelectElement).value) ?: SearchOrder.Relevance)
                        }
                    }
                    SearchOrder.values().filter { SortOrderTarget.Map in it.targets }.forEach {
                        option {
                            attrs.value = it.toString()
                            attrs.selected = it == order
                            +it.toString()
                        }
                    }
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
        div("col-2 mb-3 ps-filter") {
            filters.forEachIndexed { idx, s ->
                h4(if (idx > 0) "mt-4" else "") {
                    +s.first
                }

                s.second.forEach { f ->
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
                }
            }
        }
        div("col-4 mb-3") {
            h4 {
                +"Tags"
            }

            tags {
                attrs.default = tags
                attrs.callback = {
                    setTags(it)
                }
            }
        }
    }
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
