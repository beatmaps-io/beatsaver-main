package io.beatmaps.shared

import external.Axios
import external.CancelTokenSource
import external.invoke
import io.beatmaps.api.GenericSearchResponse
import kotlinx.browser.window
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
import org.w3c.dom.events.Event
import react.MutableRefObject
import react.Props
import react.RBuilder
import react.RefObject
import react.fc
import react.memo
import react.useEffect
import react.useEffectOnce
import react.useRef
import react.useState
import kotlin.js.Promise
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.reflect.KClass

sealed interface ElementRenderer<T>

fun interface InfiniteScrollElementRenderer<T> : ElementRenderer<T> {
    fun RBuilder.invoke(it: T?)
}

fun interface IndexedInfiniteScrollElementRenderer<T> : ElementRenderer<T> {
    fun RBuilder.invoke(idx: Int, it: T?)
}

external interface InfiniteScrollProps<T> : Props {
    var resetRef: MutableRefObject<() -> Unit>
    var rowHeight: Double
    var itemsPerRow: RefObject<() -> Int>?
    var itemsPerPage: Int
    var container: RefObject<HTMLElement>
    var renderElement: ElementRenderer<T>
    var updateScrollIndex: RefObject<(Int) -> Unit>?
    var loadPage: RefObject<(Int, CancelTokenSource) -> Promise<GenericSearchResponse<T>?>>?
    var grace: Int?
    var childFilter: ((Element) -> Boolean)?
    var scrollParent: Element?
    var headerSize: Double?
}

fun <T : Any> generateInfiniteScrollComponent(clazz: KClass<T>) = memo(internalGenerateInfiniteScrollComponent<T>(clazz.simpleName ?: "Unknown"))

private fun <T> internalGenerateInfiniteScrollComponent(name: String) = fc<InfiniteScrollProps<T>>("${name}InfiniteScroll") { props ->
    val (pages, setPages) = useState(emptyMap<Int, List<T>>())

    val loading = useRef(false)
    val pagesRef = useRef<Map<Int, List<T>>>()

    val finalPage = useRef<Int>()
    val itemsPerRow = useRef<Int>()

    val visItem = useRef<Int>()
    val visPage = useRef<Int>()
    val visiblePages = useRef<IntRange>()
    val scroll = useRef<Boolean>()
    val token = useRef<CancelTokenSource>()
    val location = useRef(window.location.search)

    val itemsPerPage = useRef(props.itemsPerPage)
    val loadNextPage = useRef<() -> Unit>()

    val emptyPage = List<T?>(props.itemsPerPage) { null }

    fun rowsPerPage() = (itemsPerPage.current ?: 20) / (itemsPerRow.current ?: 1)
    fun pageHeight() = props.rowHeight * rowsPerPage()
    fun headerSize() = props.headerSize ?: 54.5
    fun beforeContent() = headerSize() + (props.grace ?: 5)

    fun lastPage() = min(
        finalPage.current ?: Int.MAX_VALUE,
        (pages.maxByOrNull { it.key }?.key ?: 0).let { alt ->
            visiblePages.current?.let {
                max(it.last, alt)
            } ?: alt
        }
    )

    fun filteredChildren() = props.container.current?.children?.asList()?.filter(props.childFilter ?: { true })

    fun currentItem(): Int {
        filteredChildren()?.forEachIndexed { idx, it ->
            val rect = it.getBoundingClientRect()
            if (rect.top >= headerSize()) {
                return idx
            }
        }
        return 0
    }

    fun scrollTo(x: Double, y: Double) {
        props.scrollParent?.scrollTo(x, y) ?: run {
            window.scrollTo(x, y)
        }
    }

    fun scrollTo(idx: Int) {
        val scrollTo = if (idx == 0) { 0.0 } else {
            val top = filteredChildren()?.get(idx)?.getBoundingClientRect()?.top ?: 0.0
            val offset = props.scrollParent?.scrollTop ?: window.pageYOffset
            top + offset - beforeContent()
        }
        scrollTo(0.0, scrollTo)
    }

    fun innerHeight() = props.scrollParent?.clientHeight ?: window.innerHeight

    fun updateState(newItem: Int = currentItem()): Int {
        val totalVisiblePages = ceil(innerHeight() / pageHeight()).toInt()
        val newPage = max(1, newItem - (itemsPerRow.current ?: 1)) / (itemsPerPage.current ?: 20)

        visItem.current = newItem
        visPage.current = newPage
        visiblePages.current = newPage.rangeTo(newPage + totalVisiblePages)

        return newPage
    }

    val onResize = { _: Event ->
        val newItemsPerRow = props.itemsPerRow?.current?.invoke() ?: 1
        if (itemsPerRow.current != newItemsPerRow) {
            visItem.current?.let { scrollTo(it) }
            itemsPerRow.current = newItemsPerRow
        }
    }

    val onScroll: (Event?) -> Unit = { _: Event? ->
        val item = currentItem()
        if (item != visItem.current && location.current == window.location.search) {
            updateState(item)
            props.updateScrollIndex?.current?.invoke(item + 1)
        }

        location.current = window.location.search
        loadNextPage.current?.invoke()
    }

    fun setPagesAndRef(newPages: Map<Int, List<T>>? = null) {
        setPages(newPages ?: LinkedHashMap())
        pagesRef.current = newPages
    }

    val onHashChange = { _: Event? ->
        val hashPos = window.location.hash.substring(1).toIntOrNull()

        val oldItem = visItem.current
        val newItem = (hashPos ?: 1) - 1
        val newPage = updateState(newItem)

        scroll.current = hashPos != null

        if (newItem == 0) {
            scrollTo(0.0, 0.0)
        } else if (pagesRef.current?.containsKey(newPage) == true) {
            scrollTo(newItem)
        } else if (oldItem != newItem) {
            // Trigger re-render
            setPagesAndRef(pagesRef.current?.toMap())
        }
    }

    loadNextPage.current = fun() {
        if (loading.current == true) return

        // Find first visible page that isn't loaded or beyond the final page
        val toLoad = visiblePages.current?.firstOrNull { finalPage.current?.let { f -> it < f } != false && pagesRef.current?.containsKey(it) != true } ?: return

        val newToken = token.current ?: Axios.CancelToken.source()
        token.current = newToken
        loading.current = true

        val shouldScroll = if (scroll.current != false) visItem.current else null
        props.loadPage?.current!!.invoke(toLoad, newToken).then { page ->
            val lastPage = page?.info?.pages?.let { (toLoad + 1) >= it } ?: // Loaded page (ie 0) is beyond the number of pages that exist (ie 1)
                (page?.docs?.size?.let { it < (itemsPerPage.current ?: 20) } == true) // Or there aren't the expected number of results which should only happen on the last page

            if (lastPage && toLoad < (finalPage.current ?: Int.MAX_VALUE)) {
                finalPage.current = toLoad
            }

            setPagesAndRef(
                page?.docs?.let { docs ->
                    (pagesRef.current ?: emptyMap()).plus(toLoad to docs)
                } ?: pagesRef.current
            )

            loading.current = false
            scroll.current = false

            shouldScroll?.let { scrollTo(it) }
            window.setTimeout(onScroll, 1)
        }.catch {
            loading.current = false
        }
    }

    // Run as part of first render, useEffect happens after the render
    if (token.current == null) {
        onHashChange(null)
        token.current = Axios.CancelToken.source()
        loadNextPage.current?.invoke()
    }

    useEffectOnce {
        cleanup {
            token.current?.cancel("Unmounted")
        }
    }

    useEffect {
        window.addEventListener("resize", onResize)
        window.addEventListener("hashchange", onHashChange)

        cleanup {
            window.removeEventListener("resize", onResize)
            window.removeEventListener("hashchange", onHashChange)
        }
    }

    useEffect(props.scrollParent) {
        val target = props.scrollParent ?: window
        target.addEventListener("scroll", onScroll)
        cleanup {
            target.removeEventListener("scroll", onScroll)
        }
    }

    useEffect(visItem.current) {
        loadNextPage.current?.invoke()
    }

    props.resetRef.current = {
        token.current?.cancel("Another request started")

        updateState(0)
        scroll.current = false
        loading.current = false
        setPagesAndRef()
        token.current = null
        finalPage.current = null
    }

    for (pIdx in 0..lastPage()) {
        (pages[pIdx] ?: emptyPage).forEachIndexed userLoop@{ localIdx, it ->
            val idx = (pIdx * props.itemsPerPage) + localIdx
            with(props.renderElement) {
                when (this) {
                    is InfiniteScrollElementRenderer -> this@fc.invoke(it)
                    is IndexedInfiniteScrollElementRenderer -> this@fc.invoke(idx, it)
                }
            }
        }
    }
}
