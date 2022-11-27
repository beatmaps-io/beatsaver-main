package io.beatmaps.user

import external.Axios
import external.CancelTokenSource
import external.Moment
import external.generateConfig
import io.beatmaps.api.UserDetail
import io.beatmaps.common.Config
import io.beatmaps.common.fixedStr
import io.beatmaps.common.formatTime
import io.beatmaps.dateFormat
import io.beatmaps.setPageTitle
import io.beatmaps.shared.IndexedInfiniteScrollElementRenderer
import io.beatmaps.shared.InfiniteScroll
import kotlinx.datetime.Clock
import kotlinx.html.title
import org.w3c.dom.HTMLTableSectionElement
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
import react.createRef
import react.dom.a
import react.dom.i
import react.dom.img
import react.dom.table
import react.dom.tbody
import react.dom.td
import react.dom.th
import react.dom.thead
import react.dom.tr
import react.router.dom.RouteResultHistory
import react.router.dom.routeLink

external interface UserListProps : RProps {
    var history: RouteResultHistory
}

class UserList : RComponent<UserListProps, RState>() {
    private val resultsTable = createRef<HTMLTableSectionElement>()

    override fun componentDidMount() {
        setPageTitle("Mappers")
    }

    private val loadPage = { toLoad: Int, token: CancelTokenSource ->
        Axios.get<Array<UserDetail>>(
            "${Config.apibase}/users/list/$toLoad",
            generateConfig<String, Array<UserDetail>>(token.token)
        ).then {
            return@then it.data.toList()
        }
    }

    override fun RBuilder.render() {
        table("table table-dark table-striped mappers") {
            thead {
                tr {
                    th { +"#" }
                    th { i("fas fa-image") { attrs.title = "Avatar" } }
                    th { +"Mapper" }
                    th { i("fas fa-tachometer-alt") { attrs.title = "Avg BPM" } }
                    th { i("fas fa-clock") { attrs.title = "Avg Duration" } }
                    th { i("fas fa-thumbs-up") { attrs.title = "Total Upvotes" } }
                    th { i("fas fa-thumbs-down") { attrs.title = "Total Downvotes" } }
                    th { i("fas fa-percentage") { attrs.title = "Ratio" } }
                    th { i("fas fa-map-marked") { attrs.title = "Total Maps" } }
                    th { i("fas fa-star") { attrs.title = "Ranked Maps" } }
                    th { +"First" }
                    th { +"Last" }
                    th { +"Since" }
                    th { +"Age" }
                    th { +"Playlist" }
                }
            }
            tbody {
                ref = resultsTable
                key = "mapperTable"

                child(UserInfiniteScroll::class) {
                    attrs.rowHeight = 54.0
                    attrs.itemsPerPage = 20
                    attrs.container = resultsTable
                    attrs.loadPage = loadPage
                    attrs.updateScrollIndex = {
                        props.history.replace("/mappers#$it")
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
                                        val total = ((u.stats.totalUpvotes + u.stats.totalDownvotes) * 0.01f)
                                        +"${(u.stats.totalUpvotes / if (total < 0.01f) 0.01f else total).fixedStr(2)}%"
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
                                    td {
                                        attrs.colSpan = "11"
                                    }
                                }
                                td {
                                    a("${Config.apibase}/users/id/${u.id}/playlist", "_blank", "btn btn-secondary") {
                                        attrs.attributes["download"] = ""
                                        i("fas fa-list") { }
                                    }
                                }
                            } else {
                                td {
                                    attrs.colSpan = "14"
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class UserInfiniteScroll : InfiniteScroll<UserDetail>()

fun RBuilder.userList(handler: UserListProps.() -> Unit): ReactElement {
    return child(UserList::class) {
        this.attrs(handler)
    }
}
