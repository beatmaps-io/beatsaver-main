package io.beatmaps.user.list

import external.Axios
import external.CancelTokenSource
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.History
import io.beatmaps.api.GenericSearchResponse
import io.beatmaps.api.UserDetail
import io.beatmaps.api.UserSearchResponse
import io.beatmaps.common.api.ApiOrder
import io.beatmaps.common.api.UserSearchSort
import io.beatmaps.configContext
import io.beatmaps.setPageTitle
import io.beatmaps.shared.IndexedInfiniteScrollElementRenderer
import io.beatmaps.shared.generateInfiniteScrollComponent
import io.beatmaps.util.buildURL
import io.beatmaps.util.useDidUpdateEffect
import org.w3c.dom.HTMLElement
import org.w3c.dom.url.URLSearchParams
import react.Props
import react.dom.table
import react.dom.tbody
import react.dom.thead
import react.dom.tr
import react.fc
import react.router.useLocation
import react.router.useNavigate
import react.useCallback
import react.useContext
import react.useEffect
import react.useEffectOnce
import react.useMemo
import react.useRef
import react.useState
import kotlin.js.Promise

fun Int.toLocaleString(locale: String? = undefined): String = asDynamic().toLocaleString(locale) as String

val userList = fc<Props>("userList") {
    val location = useLocation()

    val (urlSearch, urlOrder) = URLSearchParams(location.search).let { params ->
        val s = UserSearchSort.fromString(params.get("sort"))
            ?.let { MapperColumn.fromSort(it) } ?: MapperColumn.UPVOTES
        val d = ApiOrder.fromString(params.get("order")) ?: ApiOrder.DESC

        s to d
    }

    val resultsTable = useRef<HTMLElement>()
    val resetRef = useRef<() -> Unit>()
    val usiRef = useRef<(Int) -> Unit>()
    val loadPageRef = useRef<(Int, CancelTokenSource) -> Promise<GenericSearchResponse<UserDetail>?>>()
    val (sort, setSort) = useState(urlSearch)
    val (order, setOrder) = useState(urlOrder)

    val config = useContext(configContext)
    val history = History(useNavigate())

    useEffectOnce {
        setPageTitle("Mappers")
    }

    useDidUpdateEffect(sort, order) {
        resetRef.current?.invoke()
    }

    useEffect(location.search) {
        if (urlSearch != sort) setSort(urlSearch)
        if (urlOrder != order) setOrder(urlOrder)
    }
    loadPageRef.current = { toLoad: Int, token: CancelTokenSource ->
        Axios.get<UserSearchResponse>(
            "${Config.apibase}/users/${if (config?.v2Search == true) "search" else "list"}/$toLoad?sort=${sort.sortEnum}&order=$order",
            generateConfig<String, UserSearchResponse>(token.token)
        ).then { it.data }
    }

    fun toURL(sortLocal: UserSearchSort?, orderLocal: ApiOrder, row: Int? = null) {
        val state = if (sortLocal == sort.sortEnum && orderLocal == order) this else null
        buildURL(
            listOfNotNull(
                (if (sortLocal != null && sortLocal != UserSearchSort.UPVOTES) "sort=$sortLocal" else null),
                (if (orderLocal != ApiOrder.DESC) "order=$orderLocal" else null)
            ),
            "mappers", row, state, history
        )
    }

    usiRef.current = { idx ->
        val hash = if (idx > 1) idx else null
        toURL(sort.sortEnum, order, hash)
    }

    val renderer = useMemo {
        IndexedInfiniteScrollElementRenderer<UserDetail> { idx, u ->
            userListRow {
                attrs.idx = idx + 1
                attrs.user = u
            }
        }
    }

    table("table table-dark table-striped mappers") {
        thead {
            tr {
                MapperColumn.entries.forEach { col ->
                    sortTh {
                        attrs.column = col
                        attrs.sort = sort
                        attrs.order = order
                        attrs.updateSort = useCallback(sort, order) { s, d ->
                            toURL(s.sortEnum, d)
                            setSort(s)
                            setOrder(d)
                        }
                    }
                }
            }
        }
        tbody {
            ref = resultsTable
            key = "mapperTable"

            userInfiniteScroll {
                attrs.resetRef = resetRef
                attrs.rowHeight = 54.0
                attrs.itemsPerPage = 20
                attrs.headerSize = 94.0
                attrs.container = resultsTable
                attrs.loadPage = loadPageRef
                attrs.updateScrollIndex = usiRef
                attrs.renderElement = renderer
            }
        }
    }
}

val userInfiniteScroll = generateInfiniteScrollComponent(UserDetail::class)
