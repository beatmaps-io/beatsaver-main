package io.beatmaps.user.list

import external.Axios
import external.CancelTokenSource
import external.Moment
import external.generateConfig
import external.routeLink
import io.beatmaps.Config
import io.beatmaps.Config.dateFormat
import io.beatmaps.History
import io.beatmaps.api.UserDetail
import io.beatmaps.api.UserSearchResponse
import io.beatmaps.common.api.ApiOrder
import io.beatmaps.common.api.UserSearchSort
import io.beatmaps.common.fixedStr
import io.beatmaps.common.formatTime
import io.beatmaps.setPageTitle
import io.beatmaps.shared.IndexedInfiniteScrollElementRenderer
import io.beatmaps.shared.InfiniteScroll
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
import react.useEffect
import react.useEffectOnce
import react.useRef
import react.useState

val userList = fc<Props> {
    val location = useLocation()

    val (urlSearch, urlOrder) = URLSearchParams(location.search).let { params ->
        val s = UserSearchSort.fromString(params.get("sort"))
            ?.let { MapperColumn.fromSort(it) } ?: MapperColumn.UPVOTES
        val d = ApiOrder.fromString(params.get("order")) ?: ApiOrder.DESC

        s to d
    }

    val resultsTable = useRef<HTMLElement>()
    val (resultsKey, setResultsKey) = useState(Any())
    val (sort, setSort) = useState(urlSearch)
    val (order, setOrder) = useState(urlOrder)

    val history = History(useNavigate())

    useEffectOnce {
        setPageTitle("Mappers")
    }

    useDidUpdateEffect(sort, order) {
        setResultsKey(Any())
    }

    useEffect(location.search) {
        if (urlSearch != sort) setSort(urlSearch)
        if (urlOrder != order) setOrder(urlOrder)
    }

    val loadPage = { toLoad: Int, token: CancelTokenSource ->
        Axios.get<UserSearchResponse>(
            "${Config.apibase}/users/search/$toLoad?sort=${sort.sortEnum}&order=$order",
            generateConfig<String, UserSearchResponse>(token.token)
        ).then {
            return@then it.data.docs
        }
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

            child(UserInfiniteScroll::class) {
                attrs.resultsKey = resultsKey
                attrs.rowHeight = 54.0
                attrs.itemsPerPage = 20
                attrs.headerSize = 94.0
                attrs.container = resultsTable
                attrs.loadPage = loadPage
                attrs.updateScrollIndex = {
                    val hash = if (it > 1) it else null
                    toURL(sort.sortEnum, order, hash)
                }
                attrs.renderElement = IndexedInfiniteScrollElementRenderer { idx, u ->
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
                                    +"${u.stats.totalUpvotes}"
                                }
                                td {
                                    +"${u.stats.totalDownvotes}"
                                }
                                td {
                                    val total = ((u.stats.totalUpvotes + u.stats.totalDownvotes + 0.001f) * 0.01f)
                                    +"${(u.stats.totalUpvotes / total).fixedStr(2)}%"
                                }
                                td {
                                    +"${u.stats.totalMaps}"
                                }
                                td {
                                    +"${u.stats.rankedMaps}"
                                }
                                td {
                                    +Moment(u.stats.firstUpload.toString()).format(dateFormat)
                                }
                                td {
                                    +Moment(u.stats.lastUpload.toString()).format(dateFormat)
                                }
                                td {
                                    u.stats.lastUpload?.let {
                                        val diff = (Clock.System.now() - it).inWholeDays
                                        +"$diff"
                                    }
                                }
                                td {
                                    if (u.stats.lastUpload != null && u.stats.firstUpload != null) {
                                        val diff = (u.stats.lastUpload - u.stats.firstUpload).inWholeDays
                                        +"$diff"
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
        }
    }
}

class UserInfiniteScroll : InfiniteScroll<UserDetail>()
