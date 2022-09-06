package io.beatmaps.shared

import external.Axios
import external.CancelTokenSource
import external.invoke
import kotlinx.browser.window
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
import org.w3c.dom.events.Event
import react.RBuilder
import react.RComponent
import react.RProps
import react.RReadableRef
import react.RState
import react.setState
import kotlin.js.Promise
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

sealed interface ElementRenderer<T>

fun interface InfiniteScrollElementRenderer<T> : ElementRenderer<T> {
    fun RBuilder.invoke(it: T?)
}

fun interface IndexedInfiniteScrollElementRenderer<T> : ElementRenderer<T> {
    fun RBuilder.invoke(idx: Int, it: T?)
}

external interface InfiniteScrollProps<T> : RProps {
    var resultsKey: Any?
    var rowHeight: Double
    var itemsPerRow: (() -> Int)?
    var itemsPerPage: Int
    var container: RReadableRef<HTMLElement>
    var renderElement: ElementRenderer<T>
    var updateScrollIndex: ((Int) -> Unit)?
    var loadPage: (Int, CancelTokenSource) -> Promise<List<T>?>
    var grace: Int?
    var childFilter: ((Element) -> Boolean)?
}

external interface InfiniteScrollState<T> : RState {
    var itemsPerRow: Int?
    var visiblePages: IntRange?
    var visItem: Int?
    var visPage: Int?
    var scroll: Boolean?
    var loading: Boolean?
    var token: CancelTokenSource?
    var pages: Map<Int, List<T>>?
    var finalPage: Int?
}

open class InfiniteScroll<T> : RComponent<InfiniteScrollProps<T>, InfiniteScrollState<T>>() {
    private val emptyPage = List<T?>(20) { null }
    private fun lastPage() = min(
        state.finalPage ?: Int.MAX_VALUE,
        (state.pages?.maxByOrNull { it.key }?.key ?: 0).let { alt ->
            state.visiblePages?.let {
                max(it.last, alt)
            } ?: alt
        }
    )

    private fun rowsPerPage() = props.itemsPerPage / (state.itemsPerRow ?: 1)
    private fun pageHeight() = props.rowHeight * rowsPerPage()
    private val headerSize = 54.5
    private fun beforeContent() = headerSize + (props.grace ?: 5)

    override fun componentDidMount() {
        onHashChange(null)

        window.addEventListener("scroll", onScroll)
        window.addEventListener("resize", onResize)
        window.addEventListener("hashchange", onHashChange)
    }

    override fun componentWillUnmount() {
        state.token?.cancel("Unmounted")

        window.removeEventListener("scroll", onScroll)
        window.removeEventListener("resize", onResize)
        window.removeEventListener("hashchange", onHashChange)
    }

    override fun componentWillUpdate(nextProps: InfiniteScrollProps<T>, nextState: InfiniteScrollState<T>) {
        if (nextProps.resultsKey !== props.resultsKey) {
            state.token?.cancel("Another request started")
            nextState.apply {
                updateState(0)

                scroll = false
                loading = false
                pages = mapOf()
                token = Axios.CancelToken.source()
                finalPage = null
            }

            window.setTimeout({
                window.scrollTo(0.0, 0.0)
                loadNextPage()
            }, 0)
        }
    }

    override fun componentDidUpdate(prevProps: InfiniteScrollProps<T>, prevState: InfiniteScrollState<T>, snapshot: Any) {
        if (state.visItem != prevState.visItem) {
            loadNextPage()
        }
    }

    val loading = { newState: Boolean -> setState { loading = newState } }

    private fun loadNextPage() {
        if (state.loading == true)
            return

        // Find first visible page that isn't loaded or beyond the final page
        val toLoad = state.visiblePages?.firstOrNull { state.finalPage?.let { f -> it < f } != false && state.pages?.containsKey(it) != true } ?: return

        val newToken = state.token ?: Axios.CancelToken.source()
        setState {
            loading = true
            if (token == null) {
                token = newToken
            }
        }

        val shouldScroll = if (state.scroll != false) state.visItem else null
        props.loadPage(toLoad, newToken).then { page ->
            setState {
                if (page?.size?.let { s -> s < props.itemsPerPage } == true && toLoad < (finalPage ?: Int.MAX_VALUE)) {
                    finalPage = toLoad
                }
                pages = if (page != null) {
                    (pages ?: mapOf()).plus(toLoad to page.toList())
                } else {
                    pages
                }

                loading = false
                scroll = false
            }

            shouldScroll?.let { scrollTo(it) }
            window.setTimeout(onScroll, 1)
        }.catch { loading(false) }
    }

    private val onHashChange = { _: Event? ->
        val hashPos = window.location.hash.substring(1).toIntOrNull()

        val newItem = (hashPos ?: 1) - 1

        setState {
            val newPage = updateState(newItem)
            scroll = hashPos != null

            if (newItem == 0) {
                window.scrollTo(0.0, 0.0)
            } else if (state.pages?.containsKey(newPage) == true) {
                scrollTo(newItem)
            }
        }
    }

    private val onResize = { _: Event ->
        val newItemsPerRow = props.itemsPerRow?.invoke() ?: 1
        if (state.itemsPerRow != newItemsPerRow) {
            state.visItem?.let { scrollTo(it) }
            setState {
                itemsPerRow = newItemsPerRow
            }
        }
    }

    private val onScroll = { _: Event? ->
        val item = currentItem()
        if (item != state.visItem) {
            setState {
                updateState(item)
            }
            props.updateScrollIndex?.invoke(item + 1)
        }

        loadNextPage()
    }

    private fun InfiniteScrollState<T>.updateState(newItem: Int = currentItem()): Int {
        val totalVisiblePages = ceil(window.innerHeight / pageHeight()).toInt()
        val newPage = max(1, newItem - (state.itemsPerRow ?: 1)) / props.itemsPerPage

        visItem = newItem
        visPage = newPage
        visiblePages = newPage.rangeTo(newPage + totalVisiblePages)

        return newPage
    }

    private fun currentItem(): Int {
        props.container.current?.children?.asList()?.filter(props.childFilter ?: { true })?.forEachIndexed { idx, it ->
            val rect = it.getBoundingClientRect()
            if (rect.top >= headerSize) {
                return idx
            }
        }
        return 0
    }

    private fun scrollTo(idx: Int) {
        val scrollTo = if (idx == 0) 0.0 else {
            val top = props.container.current?.children?.asList()?.get(idx)?.getBoundingClientRect()?.top ?: 0.0
            top + window.pageYOffset - beforeContent()
        }
        window.scrollTo(0.0, scrollTo)
    }

    override fun RBuilder.render() {
        for (pIdx in 0..lastPage()) {
            (state.pages?.get(pIdx) ?: emptyPage).forEachIndexed userLoop@{ localIdx, it ->
                val idx = (pIdx * props.itemsPerPage) + localIdx
                with(props.renderElement) {
                    when (this) {
                        is InfiniteScrollElementRenderer -> this@render.invoke(it)
                        is IndexedInfiniteScrollElementRenderer -> this@render.invoke(idx, it)
                    }
                }
            }
        }
    }
}
