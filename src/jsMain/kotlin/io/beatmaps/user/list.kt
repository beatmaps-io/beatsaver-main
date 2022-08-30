package io.beatmaps.user

import external.Moment
import external.axiosGet
import io.beatmaps.api.UserDetail
import io.beatmaps.common.Config
import io.beatmaps.common.fixedStr
import io.beatmaps.common.formatTime
import io.beatmaps.dateFormat
import io.beatmaps.setPageTitle
import kotlinx.browser.window
import kotlinx.datetime.Clock
import kotlinx.html.title
import org.w3c.dom.HashChangeEvent
import org.w3c.dom.events.Event
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
import react.dom.a
import react.dom.i
import react.dom.img
import react.dom.jsStyle
import react.dom.table
import react.dom.tbody
import react.dom.td
import react.dom.th
import react.dom.thead
import react.dom.tr
import react.router.dom.RouteResultHistory
import react.router.dom.routeLink
import react.setState
import kotlin.math.ceil
import kotlin.math.max

external interface UserListProps : RProps {
    var history: RouteResultHistory
}

external interface UserListState : RState {
    var pages: Map<Int, List<UserDetail>>
    var loading: Boolean
    var visItem: Int
    var visPage: Int
    var visiblePages: IntRange
    var scroll: Boolean
}

class UserList : RComponent<UserListProps, UserListState>() {

    private val rowHeight = 53.781
    private val usersPerPage = 20
    private val pageHeight = rowHeight * usersPerPage

    override fun componentWillMount() {
        setState {
            pages = mapOf()
            loading = false
            visItem = -1
            visPage = -1
            visiblePages = IntRange.EMPTY
            scroll = true
        }
    }

    private fun updateFromHash(e: Event?) {
        val totalVisiblePages = ceil(window.innerHeight / pageHeight).toInt()
        val hashPos = window.location.hash.substring(1).toIntOrNull()
        setState {
            visItem = (hashPos ?: 1) - 1
            visPage = visItem / usersPerPage
            visiblePages = visPage.rangeTo(visPage + totalVisiblePages)
            scroll = hashPos != null

            if (pages.containsKey(visPage)) {
                window.scrollTo(0.0, (rowHeight * visItem) + 20)
            }
        }
    }

    override fun componentDidUpdate(prevProps: UserListProps, prevState: UserListState, snapshot: Any) {
        if (state.visItem != prevState.visItem) {
            loadNextPage()
        }
    }

    override fun componentDidMount() {
        setPageTitle("Mappers")
        updateFromHash(null)

        window.addEventListener("hashchange", ::updateFromHash)
    }

    override fun componentWillUnmount() {
        window.removeEventListener("hashchange", ::updateFromHash)
        window.removeEventListener("scroll", ::handleScroll)
    }

    private fun loadNextPage() {
        if (state.loading)
            return

        val toLoad = state.visiblePages.firstOrNull { !state.pages.containsKey(it) } ?: return

        setState {
            loading = true
        }

        axiosGet<Array<UserDetail>>("${Config.apibase}/users/list/$toLoad").then {
            val shouldScroll = state.scroll
            setState {
                loading = it.data.isEmpty()
                pages = pages.plus(toLoad to it.data.toList())
                scroll = false
            }
            if (shouldScroll) {
                window.scrollTo(0.0, (rowHeight * state.visItem) + 20)
            }
            window.addEventListener("scroll", ::handleScroll)
            if (it.data.isNotEmpty()) {
                window.setTimeout(::handleScroll, 1)
            }
        }.catch {
            setState {
                loading = false
            }
        }
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
                visPage = item / usersPerPage
                visiblePages = visPage.rangeTo(visPage + totalVisiblePages)
            }
            props.history.replace("/mappers#${item + 1}")
        }

        loadNextPage()
    }

    private fun lastPage() = max(state.visiblePages.last, state.pages.maxByOrNull { it.key }?.key ?: 0)

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
                for (pIdx in 0..lastPage()) {
                    state.pages[pIdx]?.let {
                        it.forEachIndexed userLoop@{ uIdx, u ->
                            val idx = (pIdx * 20) + uIdx

                            if (u.stats == null) return@userLoop

                            tr {
                                td {
                                    +"${idx+1}"
                                }
                                td {
                                    img("${u.name} avatar", u.avatar, classes = "rounded-circle") {
                                        attrs.width = "40"
                                        attrs.height = "40"
                                    }
                                }
                                td {
                                    routeLink("/profile/${u.id}") {
                                        +u.name
                                    }
                                }
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
                                td {
                                    a("${Config.apibase}/users/id/${u.id}/playlist", "_blank", "btn btn-secondary") {
                                        attrs.attributes["download"] = ""
                                        i("fas fa-list") { }
                                    }
                                }
                            }
                        }
                    } ?: run {
                        tr {
                            attrs.jsStyle {
                                height = "${pageHeight}px"
                            }
                        }
                        // Empty space time
                    }
                }
            }
        }
    }
}

fun RBuilder.userList(handler: UserListProps.() -> Unit): ReactElement {
    return child(UserList::class) {
        this.attrs(handler)
    }
}
