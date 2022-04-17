package io.beatmaps.index

import external.Axios
import external.CancelTokenSource
import external.generateConfig
import external.invoke
import io.beatmaps.api.MapDetail
import io.beatmaps.api.SearchOrder
import io.beatmaps.api.SearchResponse
import io.beatmaps.api.UserDetail
import io.beatmaps.common.Config
import kotlinx.browser.window
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HashChangeEvent
import org.w3c.dom.asList
import org.w3c.dom.events.Event
import react.RBuilder
import react.RComponent
import react.RProps
import react.RReadableRef
import react.RState
import react.ReactElement
import react.createRef
import react.dom.div
import react.dom.h4
import react.dom.img
import react.dom.p
import react.router.dom.RouteResultHistory
import react.router.dom.routeLink
import react.setState
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

external interface BeatmapTableProps : RProps {
    var search: SearchParams?
    var user: Int?
    var curated: Boolean?
    var wip: Boolean?
    var modal: RReadableRef<ModalComponent>
    var history: RouteResultHistory
    var updateScrollIndex: ((Int) -> Unit)?
    var visible: Boolean?
}

data class SearchParams(
    val search: String,
    val automapper: Boolean?,
    val minNps: Float?,
    val maxNps: Float?,
    val chroma: Boolean?,
    val sortOrder: SearchOrder,
    val from: String?,
    val to: String?,
    val noodle: Boolean?,
    val ranked: Boolean?,
    val curated: Boolean?,
    val verified: Boolean?,
    val fullSpread: Boolean?,
    val me: Boolean?,
    val cinema: Boolean?,
    val tags: List<String>
)

external interface BeatmapTableState : RState {
    var shouldClear: Boolean
    var user: UserDetail?
    var token: CancelTokenSource

    var pages: Map<Int, List<MapDetail>>
    var loading: Boolean
    var visItem: Int
    var visPage: Int
    var finalPage: Int
    var visiblePages: IntRange
    var scroll: Boolean

    var itemsPerRow: Int
}

external fun encodeURIComponent(uri: String): String

class BeatmapTable : RComponent<BeatmapTableProps, BeatmapTableState>() {

    private val emptyPage = List<MapDetail?>(20) { null }
    private val resultsTable = createRef<HTMLDivElement>()

    private val rowHeight = 155.0 // At least, can be more
    private fun itemsPerRow() = if (window.innerWidth < 992) 1 else 2
    private val itemsPerPage = 20
    private val beforeContent = 60
    private fun rowsPerPage() = itemsPerPage / itemsPerRow()
    private fun pageHeight() = rowHeight * rowsPerPage()

    override fun componentWillMount() {
        setState {
            shouldClear = false
            user = null
            token = Axios.CancelToken.source()

            pages = mapOf()
            loading = false
            visItem = -1
            visPage = -1
            finalPage = Int.MAX_VALUE
            visiblePages = IntRange.EMPTY
            scroll = true
        }
    }

    private fun scrollTo(idx: Int) {
        val top = resultsTable.current?.children?.asList()?.get(idx)?.getBoundingClientRect()?.top ?: 0.0
        val scrollTo = top + window.pageYOffset - beforeContent
        window.scrollTo(0.0, scrollTo)
    }

    private fun currentItem(): Int {
        resultsTable.current?.children?.asList()?.forEachIndexed { idx, it ->
            val rect = it.getBoundingClientRect()
            if (rect.top >= beforeContent) {
                return idx
            }
        }
        return 0
    }

    private fun updateFromHash(e: HashChangeEvent?) {
        val totalVisiblePages = ceil(window.innerHeight / pageHeight()).toInt()
        val hashPos = window.location.hash.substring(1).toIntOrNull()
        setState {
            itemsPerRow = itemsPerRow()
            visItem = (hashPos ?: 1) - 1
            visPage = max(1, visItem - itemsPerRow()) / itemsPerPage
            visiblePages = visPage.rangeTo(visPage + totalVisiblePages)
            scroll = hashPos != null

            if (visPage == 0) {
                window.scrollTo(0.0, 0.0)
            } else if (pages.containsKey(visPage)) {
                scrollTo(visItem)
            }
        }
    }

    override fun componentDidUpdate(prevProps: BeatmapTableProps, prevState: BeatmapTableState, snapshot: Any) {
        if (state.visItem != prevState.visItem) {
            loadNextPage()
        }
    }

    override fun componentDidMount() {
        updateFromHash(null)

        window.onhashchange = ::updateFromHash
    }

    override fun componentWillUpdate(nextProps: BeatmapTableProps, nextState: BeatmapTableState) {
        if (props.user != nextProps.user || props.wip != nextProps.wip || props.curated != nextProps.curated || props.search !== nextProps.search) {
            state.token.cancel.invoke("Another request started")

            val windowSize = window.innerHeight
            val totalVisiblePages = ceil(windowSize / pageHeight()).toInt()
            nextState.apply {
                loading = false

                shouldClear = true
                user = null
                token = Axios.CancelToken.source()

                pages = mapOf()
                visItem = 0
                visPage = 0
                finalPage = Int.MAX_VALUE
                visiblePages = visPage.rangeTo(visPage + totalVisiblePages)
                scroll = false
            }

            window.setTimeout(::loadNextPage, 0)
        }
    }

    private fun lastPage() = min(state.finalPage, max(state.visiblePages.last, state.pages.maxByOrNull { it.key }?.key ?: 0))

    private fun getUrl(page: Int) =
        if (props.wip == true) {
            "${Config.apibase}/maps/wip/$page"
        } else if (props.curated == true && props.user != null) {
            "${Config.apibase}/search/text/$page?sortOrder=Curated&curator=${props.user}&automapper=true"
        } else if (props.user != null) {
            "${Config.apibase}/search/text/$page?mapper=${props.user}&automapper=true"
        } else {
            props.search?.let { search ->
                "${Config.apibase}/search/text/$page?sortOrder=${search.sortOrder}" +
                    (if (search.automapper != null) "&automapper=${search.automapper}" else "") +
                    (if (search.chroma != null) "&chroma=${search.chroma}" else "") +
                    (if (search.noodle != null) "&noodle=${search.noodle}" else "") +
                    (if (search.me != null) "&me=${search.me}" else "") +
                    (if (search.cinema != null) "&cinema=${search.cinema}" else "") +
                    (if (search.ranked != null) "&ranked=${search.ranked}" else "") +
                    (if (search.curated != null) "&curated=${search.curated}" else "") +
                    (if (search.verified != null) "&verified=${search.verified}" else "") +
                    (if (search.fullSpread != null) "&fullSpread=${search.fullSpread}" else "") +
                    (if (search.search.isNotBlank()) "&q=${encodeURIComponent(search.search)}" else "") +
                    (if (search.maxNps != null) "&maxNps=${search.maxNps}" else "") +
                    (if (search.minNps != null) "&minNps=${search.minNps}" else "") +
                    (if (search.from != null) "&from=${search.from}" else "") +
                    (if (search.to != null) "&to=${search.to}" else "") +
                    (if (search.tags.isNotEmpty()) "&tags=${search.tags.joinToString(",")}" else "")
            } ?: ""
        }

    private val hashRegex = Regex("^[A-Za-z0-9]{40}$")
    private fun redirectIfHash() =
        props.search?.let { search ->
            if (hashRegex.matches(search.search)) {
                Axios.get<MapDetail>(
                    "${Config.apibase}/maps/hash/${encodeURIComponent(search.search)}",
                    generateConfig<String, MapDetail>(state.token.token)
                ).then {
                    val dyn: dynamic = props.history
                    dyn.replace("/maps/" + it.data.id)
                }.catch {
                    // Ignore errors, this is only a secondary request
                }

                true
            } else {
                false
            }
        } ?: false

    private fun loadNextPage() {
        if (state.loading)
            return

        val toLoad = state.visiblePages.firstOrNull { !state.pages.containsKey(it) } ?: return

        setState {
            loading = true
        }

        if (redirectIfHash())
            return

        props.search?.let { search ->
            if (toLoad == 0 && props.wip != true && props.curated != true && props.user == null && search.search.length > 2) {
                Axios.get<UserDetail>(
                    "${Config.apibase}/users/name/${encodeURIComponent(search.search)}",
                    generateConfig<String, UserDetail>(state.token.token)
                ).then {
                    setState {
                        user = it.data
                    }
                }.catch {
                    // Ignore errors, this is only a secondary request
                }
            }
        }

        Axios.get<SearchResponse>(
            getUrl(toLoad),
            generateConfig<String, SearchResponse>(state.token.token)
        ).then {
            if (it.data.redirect != null) {
                val dyn: dynamic = props.history
                dyn.replace("/maps/" + it.data.redirect)
                return@then
            }

            val shouldScroll = state.scroll
            val page = it.data.docs
            setState {
                loading = false
                if (page?.isEmpty() == true && toLoad < finalPage) {
                    finalPage = toLoad
                }
                pages = if (shouldClear) { mapOf() } else { pages }.let {
                    if (page != null) {
                        pages.plus(toLoad to page.toList())
                    } else {
                        pages
                    }
                }
                scroll = false
            }

            if (shouldScroll) {
                scrollTo(state.visItem)
            }
            window.onscroll = ::handleScroll
            window.onresize = ::reportWindow
            window.setTimeout(::handleScroll, 1)
        }.catch {
            // Cancelled request?
            setState {
                loading = false
            }
        }
    }

    override fun componentWillUnmount() {
        window.onscroll = null
        window.onresize = null
    }

    @Suppress("UNUSED_PARAMETER")
    private fun handleScroll(e: Event) {
        val windowSize = window.innerHeight

        val item = currentItem()
        if (item != state.visItem) {
            val totalVisiblePages = ceil(windowSize / pageHeight()).toInt()
            setState {
                visItem = item
                visPage = max(1, item - itemsPerRow()) / itemsPerPage
                visiblePages = visPage.rangeTo(visPage + totalVisiblePages)
            }
            props.updateScrollIndex?.invoke(item + 1)
        }

        loadNextPage()
    }

    private fun reportWindow(e: Event) {
        if (state.itemsPerRow != itemsPerRow()) {
            scrollTo(state.visItem)
            setState {
                itemsPerRow = itemsPerRow()
            }
        }
    }

    override fun RBuilder.render() {
        if (props.visible == false) return

        state.user?.let {
            routeLink("/profile/${it.id}", className = "card border-dark user-suggestion-card") {
                div("card-body") {
                    h4("card-title") {
                        +"Were you looking for:"
                    }
                    p("card-text") {
                        img("${it.name} avatar", it.avatar, classes = "rounded-circle") {
                            attrs.width = "40"
                            attrs.height = "40"
                        }
                        +it.name
                    }
                }
            }
        }
        div("search-results") {
            ref = resultsTable
            for (pIdx in 0..lastPage()) {
                (state.pages[pIdx] ?: emptyPage).forEach userLoop@{ it ->
                    beatmapInfo {
                        map = it
                        version = it?.let { if (props.wip == true) it.latestVersion() else it.publishedVersion() }
                        modal = props.modal
                    }
                }
            }
        }
    }
}

fun RBuilder.beatmapTable(handler: BeatmapTableProps.() -> Unit): ReactElement {
    return child(BeatmapTable::class) {
        this.attrs(handler)
    }
}
