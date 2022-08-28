package io.beatmaps.playlist

import external.Axios
import external.generateConfig
import io.beatmaps.api.PlaylistFull
import io.beatmaps.api.PlaylistSearchResponse
import io.beatmaps.api.SearchOrder
import io.beatmaps.common.Config
import io.beatmaps.index.encodeURIComponent
import kotlinx.browser.window
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.events.Event
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
import react.createRef
import react.dom.div
import react.router.dom.RouteResultHistory
import react.router.dom.routeLink
import react.setState
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

data class PlaylistSearchParams(
    val search: String,
    val minNps: Float?,
    val maxNps: Float?,
    val from: String? = null,
    val to: String? = null,
    val includeEmpty: Boolean? = null,
    val curated: Boolean? = null,
    val verified: Boolean? = null,
    val sortOrder: SearchOrder
)

external interface PlaylistTableProps : RProps {
    var search: PlaylistSearchParams?
    var userId: Int?
    var own: Boolean?
    var history: RouteResultHistory
    var visible: Boolean?
    var updateScrollIndex: ((Int) -> Unit)?
}

external interface PlaylistTableState : RState {
    var pages: Map<Int, List<PlaylistFull>>
    var loading: Boolean?
    var visItem: Int
    var visPage: Int
    var finalPage: Int
    var visiblePages: IntRange
}

class PlaylistTable : RComponent<PlaylistTableProps, PlaylistTableState>() {
    private val emptyPage = List<PlaylistFull?>(20) { null }
    private val resultsTable = createRef<HTMLDivElement>()

    private val playlistsPerPage = 20
    private val rowHeight = 80.0

    private val pageHeight = rowHeight * playlistsPerPage

    override fun componentWillMount() {
        val totalVisiblePages = ceil(window.innerHeight / pageHeight).toInt()
        setState {
            pages = mapOf()
            loading = false
            visItem = 0
            visPage = 0
            finalPage = Int.MAX_VALUE
            visiblePages = visPage.rangeTo(visPage + totalVisiblePages)
        }
    }

    override fun componentDidUpdate(prevProps: PlaylistTableProps, prevState: PlaylistTableState, snapshot: Any) {
        if (state.visItem != prevState.visItem) {
            loadNextPage()
        }
    }

    override fun componentDidMount() {
        loadNextPage()
    }

    override fun componentWillUpdate(nextProps: PlaylistTableProps, nextState: PlaylistTableState) {
        if (props.userId != nextProps.userId || props.search !== nextProps.search) {
            val windowSize = window.innerHeight
            val totalVisiblePages = ceil(windowSize / pageHeight).toInt()
            nextState.apply {
                loading = false

                pages = mapOf()
                visItem = 0
                visPage = 0
                finalPage = Int.MAX_VALUE
                visiblePages = visPage.rangeTo(visPage + totalVisiblePages)
            }

            window.setTimeout(::loadNextPage, 0)
        }
    }

    private fun getUrl(page: Int) =
        if (props.userId != null) {
            "${Config.apibase}/playlists/user/${props.userId}/$page"
        } else {
            props.search?.let { search ->
                "${Config.apibase}/playlists/search/$page?sortOrder=${search.sortOrder}" +
                    (if (search.curated != null) "&curated=${search.curated}" else "") +
                    (if (search.verified != null) "&verified=${search.verified}" else "") +
                    (if (search.search.isNotBlank()) "&q=${encodeURIComponent(search.search)}" else "") +
                    (if (search.maxNps != null) "&maxNps=${search.maxNps}" else "") +
                    (if (search.minNps != null) "&minNps=${search.minNps}" else "") +
                    (if (search.from != null) "&from=${search.from}" else "") +
                    (if (search.to != null) "&to=${search.to}" else "") +
                    (if (search.includeEmpty != null) "&to=${search.includeEmpty}" else "")
            } ?: ""
        }

    private fun loadNextPage() {
        if (state.loading == true)
            return

        val toLoad = state.visiblePages.firstOrNull { !state.pages.containsKey(it) } ?: return

        setState {
            loading = true
        }

        Axios.get<PlaylistSearchResponse>(
            getUrl(toLoad),
            generateConfig<String, PlaylistSearchResponse>()
        ).then {
            val page = it.data.docs
            setState {
                loading = false
                if (page.isEmpty() && toLoad < finalPage) {
                    finalPage = toLoad
                }
                pages = pages.plus(toLoad to page)
            }
            window.onscroll = ::handleScroll
            if (it.data.docs.isNotEmpty()) {
                window.setTimeout(::handleScroll, 1)
            }
        }.catch {
            // Oh noes
        }
    }

    override fun componentWillUnmount() {
        window.onscroll = null
    }

    @Suppress("UNUSED_PARAMETER")
    private fun handleScroll(e: Event) {
        val scrollPosition = window.pageYOffset
        val windowSize = window.innerHeight

        val item = ((scrollPosition - 19) / rowHeight).toInt()
        if (item != state.visItem) {
            val totalVisiblePages = ceil(windowSize / pageHeight).toInt()
            setState {
                visItem = item
                visPage = item / playlistsPerPage
                visiblePages = visPage.rangeTo(visPage + totalVisiblePages)
            }
            props.updateScrollIndex?.invoke(item + 1)
        }

        loadNextPage()
    }

    private fun lastPage() = min(state.finalPage, max(state.visiblePages.last, state.pages.maxByOrNull { it.key }?.key ?: 0))

    override fun RBuilder.render() {
        if (props.visible == false) return

        if (props.own == true) {
            routeLink("/playlists/new", className = "btn btn-success btn-sm") {
                +"Create New"
            }
        }

        div("search-results") {
            ref = resultsTable
            for (pIdx in 0..lastPage()) {
                (state.pages[pIdx] ?: emptyPage).forEach { pl ->
                    playlistInfo {
                        playlist = pl
                    }
                }
            }
        }
    }
}

fun RBuilder.playlistTable(handler: PlaylistTableProps.() -> Unit): ReactElement {
    return child(PlaylistTable::class) {
        this.attrs(handler)
    }
}
