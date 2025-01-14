package io.beatmaps.user.list

import external.Axios
import external.CancelTokenSource
import external.Moment
import external.generateConfig
import external.routeLink
import io.beatmaps.Config
import io.beatmaps.Config.dateFormat
import io.beatmaps.History
import io.beatmaps.api.GenericSearchResponse
import io.beatmaps.api.UserDetail
import io.beatmaps.api.UserSearchResponse
import io.beatmaps.common.api.ApiOrder
import io.beatmaps.common.api.UserSearchSort
import io.beatmaps.common.fixed
import io.beatmaps.common.formatTime
import io.beatmaps.configContext
import io.beatmaps.setPageTitle
import io.beatmaps.shared.IndexedInfiniteScrollElementRenderer
import io.beatmaps.shared.generateInfiniteScrollComponent
import io.beatmaps.util.buildURL
import io.beatmaps.util.useDidUpdateEffect
import kotlinx.datetime.Clock
import org.w3c.dom.HTMLElement
import org.w3c.dom.url.URLSearchParams
import react.Props
import react.dom.a
import react.dom.i
import react.dom.img
import react.dom.table
import react.dom.tbody
import react.dom.td
import react.dom.thead
import react.dom.tr
import react.fc
import react.router.useLocation
import react.router.useNavigate
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
            tr {
                td {
                    +"${idx+1}"
                }
                if (u != null) {
                    td {
                        img("${u.name} avatar", u.avatar, classes = "rounded-circle") {
                            attrs.width = "40"
                            attrs.height = "40"
                        }
                    }
                    td {
                        routeLink(u.profileLink()) {
                            +u.name
                        }
                    }
                    if (u.stats != null) {
                        td {
                            +"${u.stats.avgBpm}"
                        }
                        td {
                            +u.stats.avgDuration.formatTime()
                        }
                        td {
                            +u.stats.totalUpvotes.toLocaleString()
                        }
                        td {
                            +u.stats.totalDownvotes.toLocaleString()
                        }
                        td {
                            val total = ((u.stats.totalUpvotes + u.stats.totalDownvotes + 0.001f) * 0.01f)
                            +"${(u.stats.totalUpvotes / total).fixed(2)}%"
                        }
                        td {
                            +u.stats.totalMaps.toLocaleString()
                        }
                        td {
                            +u.stats.rankedMaps.toLocaleString()
                        }
                        td {
                            +Moment(u.stats.firstUpload.toString()).format(dateFormat)
                        }
                        td {
                            +Moment(u.stats.lastUpload.toString()).format(dateFormat)
                        }
                        td {
                            u.stats.lastUpload?.let {
                                +(Clock.System.now() - it).inWholeDays.toInt().toLocaleString()
                            }
                        }
                        td {
                            if (u.stats.lastUpload != null && u.stats.firstUpload != null) {
                                +(u.stats.lastUpload - u.stats.firstUpload).inWholeDays.toInt().toLocaleString()
                            }
                        }
                    } else {
                        repeat(11) { td { } }
                    }
                    td {
                        a("${Config.apibase}/users/id/${u.id}/playlist", "_blank", "btn btn-secondary") {
                            attrs.attributes["download"] = ""
                            i("fas fa-list") { }
                        }
                    }
                } else {
                    repeat(14) { td { } }
                }
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
                        attrs.updateSort = { s, d ->
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
