package io.beatmaps.playlist

import external.DateRangePicker
import external.Moment
import external.reactFor
import io.beatmaps.common.SearchOrder
import io.beatmaps.common.SearchParamsPlaylist
import io.beatmaps.common.SearchPlaylistConfig
import io.beatmaps.common.SortOrderTarget
import io.beatmaps.common.toSet
import io.beatmaps.common.toTags
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

val playlistSearchEditor = fc<PSEProps> { props ->
    val (minNps, setMinNps) = useState(props.config.searchParams.minNps ?: 0f)
    val (maxNps, setMaxNps) = useState(props.config.searchParams.maxNps ?: 16f)
    val (startDate, setStartDate) = useState(props.config.searchParams.from?.let { Moment(it.toString()) })
    val (endDate, setEndDate) = useState(props.config.searchParams.to?.let { Moment(it.toString()) })
    val (dateFocused, setDateFocused) = useState<String?>(null)
    val (order, setOrder) = useState(props.config.searchParams.sortOrder)
    val (mapCount, setMapCount) = useState(props.config.mapCount)
    val (tags, setTags) = useState(props.config.searchParams.tags)

    val searchRef = useRef<HTMLInputElement>()
    val aiRef = useRef<HTMLInputElement>()
    val rankedRef = useRef<HTMLInputElement>()
    val curatedRef = useRef<HTMLInputElement>()
    val verifiedRef = useRef<HTMLInputElement>()
    val fullSpreadRef = useRef<HTMLInputElement>()

    val chromaRef = useRef<HTMLInputElement>()
    val noodleRef = useRef<HTMLInputElement>()
    val meRef = useRef<HTMLInputElement>()
    val cinemaRef = useRef<HTMLInputElement>()

    fun nullIfFalse(b: Boolean?) =
        if (b != true) null else true

    fun doCallback() {
        props.callback(
            SearchPlaylistConfig(
                SearchParamsPlaylist(
                    searchRef.current?.value ?: "",
                    nullIfFalse(aiRef.current?.checked),
                    if (minNps > 0) minNps else null,
                    if (maxNps < 16) maxNps else null,
                    nullIfFalse(chromaRef.current?.checked),
                    order,
                    startDate?.let { Instant.parse(it.toISOString()) },
                    endDate?.let { Instant.parse(it.toISOString()) },
                    nullIfFalse(noodleRef.current?.checked),
                    nullIfFalse(rankedRef.current?.checked),
                    nullIfFalse(curatedRef.current?.checked),
                    nullIfFalse(verifiedRef.current?.checked),
                    nullIfFalse(fullSpreadRef.current?.checked),
                    nullIfFalse(meRef.current?.checked),
                    nullIfFalse(cinemaRef.current?.checked),
                    tags
                ),
                mapCount
            )
        )
    }

    useEffect(minNps, maxNps, startDate, endDate, order, mapCount, tags) {
        doCallback()
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
                            attrs.selected = order == it
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
                            attrs.selected = mapCount == it
                            +it.toString()
                        }
                    }
                }
            }
        }
        div("col-2 mb-3 ps-filter") {
            h4 {
                +"General"
            }
            toggle {
                attrs.id = "automapper"
                attrs.disabled = props.loading
                attrs.default = props.config.searchParams.automapper
                attrs.text = "AI"
                attrs.ref = aiRef
                attrs.block = {
                    doCallback()
                }
            }
            toggle {
                attrs.id = "ranked"
                attrs.disabled = props.loading
                attrs.default = props.config.searchParams.ranked
                attrs.text = "Ranked"
                attrs.ref = rankedRef
                attrs.block = {
                    doCallback()
                }
            }
            toggle {
                attrs.id = "curated"
                attrs.disabled = props.loading
                attrs.default = props.config.searchParams.curated
                attrs.text = "Curated"
                attrs.ref = curatedRef
                attrs.block = {
                    doCallback()
                }
            }
            toggle {
                attrs.id = "verified"
                attrs.disabled = props.loading
                attrs.default = props.config.searchParams.verified
                attrs.text = "Verified Mapper"
                attrs.ref = verifiedRef
                attrs.block = {
                    doCallback()
                }
            }
            toggle {
                attrs.id = "fullspread"
                attrs.disabled = props.loading
                attrs.default = props.config.searchParams.fullSpread
                attrs.text = "Full Spread"
                attrs.ref = fullSpreadRef
                attrs.block = {
                    doCallback()
                }
            }

            h4("mt-4") {
                +"Requirements"
            }
            toggle {
                attrs.id = "chroma"
                attrs.disabled = props.loading
                attrs.default = props.config.searchParams.chroma
                attrs.text = "Chroma"
                attrs.ref = chromaRef
                attrs.block = {
                    doCallback()
                }
            }
            toggle {
                attrs.id = "noodle"
                attrs.disabled = props.loading
                attrs.default = props.config.searchParams.noodle
                attrs.text = "Noodle"
                attrs.ref = noodleRef
                attrs.block = {
                    doCallback()
                }
            }
            toggle {
                attrs.id = "me"
                attrs.disabled = props.loading
                attrs.default = props.config.searchParams.me
                attrs.text = "Mapping Extensions"
                attrs.ref = meRef
                attrs.block = {
                    doCallback()
                }
            }
            toggle {
                attrs.id = "cinema"
                attrs.disabled = props.loading
                attrs.default = props.config.searchParams.cinema
                attrs.text = "Cinema"
                attrs.ref = cinemaRef
                attrs.block = {
                    doCallback()
                }
            }
        }
        div("col-4 mb-3") {
            h4 {
                +"Tags"
            }

            tags {
                attrs.default = props.config.searchParams.tags.toSet()
                attrs.callback = {
                    setTags(it.toTags())
                }
            }
        }
    }
}
