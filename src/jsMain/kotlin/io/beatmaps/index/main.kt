package io.beatmaps.index

import external.Moment
import io.beatmaps.WithRouterProps
import io.beatmaps.common.MapTagSet
import io.beatmaps.common.SearchOrder
import io.beatmaps.common.SearchParamsPlaylist
import io.beatmaps.common.SortOrderTarget
import io.beatmaps.common.asQuery
import io.beatmaps.common.toQuery
import io.beatmaps.common.toTagSet
import io.beatmaps.dateFormat
import io.beatmaps.shared.ExtraContentRenderer
import io.beatmaps.shared.FilterCategory
import io.beatmaps.shared.FilterInfo
import io.beatmaps.shared.SearchParamGenerator
import io.beatmaps.shared.buildURL
import io.beatmaps.shared.includeIfNotNull
import io.beatmaps.shared.queryParams
import io.beatmaps.shared.search
import io.beatmaps.shared.tags
import io.beatmaps.stateNavOptions
import kotlinx.browser.window
import kotlinx.datetime.Instant
import kotlinx.html.ButtonType
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import org.w3c.dom.url.URLSearchParams
import react.RBuilder
import react.RComponent
import react.State
import react.createRef
import react.dom.button
import react.dom.div
import react.dom.i
import react.dom.jsStyle
import react.ref
import react.setState

external interface HomePageProps : WithRouterProps

external interface HomePageState : State {
    var searchParams: SearchParams?
    var tags: MapTagSet?
}

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

inline fun <T> T.applyIf(condition: Boolean, block: T.() -> T): T = if (condition) block(this) else this

fun String.toInstant() = Instant.parse(Moment(this).toISOString())

fun SearchParams?.toPlaylistConfig() = SearchParamsPlaylist(
    this?.search ?: "",
    this?.automapper,
    this?.minNps,
    this?.maxNps,
    this?.chroma,
    this?.sortOrder ?: SearchOrder.Latest,
    this?.from?.toInstant(),
    this?.to?.toInstant(),
    this?.noodle,
    this?.ranked,
    this?.curated,
    this?.verified,
    this?.fullSpread,
    this?.me,
    this?.cinema,
    this?.tags ?: mapOf()
)

class HomePage : RComponent<HomePageProps, HomePageState>() {
    private val modalRef = createRef<ModalComponent>()

    override fun componentWillMount() {
        setState {
            searchParams = fromURL()
        }
    }

    override fun componentWillUpdate(nextProps: HomePageProps, nextState: HomePageState) {
        if (state.searchParams == nextState.searchParams) {
            val fromParams = fromURL()
            if (fromParams != state.searchParams) {
                nextState.searchParams = fromParams
            }
        }
    }

    private fun fromURL() = URLSearchParams(window.location.search).let { params ->
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

    private fun updateSearchParams(searchParamsLocal: SearchParams?, row: Int?) {
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
                "", row, state.searchParams, props.history
            )
        }

        setState {
            searchParams = searchParamsLocal
        }
    }

    override fun RBuilder.render() {
        search<SearchParams> {
            typedState = state.searchParams
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
                    this@HomePage.state.tags ?: mapOf()
                )
            }
            extraFilters = ExtraContentRenderer {
                tags {
                    attrs.default = state.tags
                    attrs.callback = {
                        setState {
                            tags = it
                        }
                    }
                }
            }
            updateUI = { params ->
                setState {
                    tags = params?.tags
                }
            }
            filterTexts = {
                (state.tags?.flatMap { y -> y.value.map { z -> (if (y.key) "" else "!") + z.slug } } ?: listOf())
            }
            updateSearchParams = ::updateSearchParams
        }
        modal {
            ref = modalRef
        }
        beatmapTable {
            search = state.searchParams
            modal = modalRef
            history = props.history
            updateScrollIndex = {
                updateSearchParams(state.searchParams, if (it < 2) null else it)
            }
        }

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

                    props.history.go(
                        "/playlists/new",
                        stateNavOptions(state.searchParams?.toPlaylistConfig(), false)
                    )
                }
                i("fas fa-list-ul") { }
            }
        }
    }
}
