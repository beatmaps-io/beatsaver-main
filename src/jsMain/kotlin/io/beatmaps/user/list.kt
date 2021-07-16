package io.beatmaps.user

import axiosGet
import external.Moment
import io.beatmaps.api.UserDetail
import io.beatmaps.common.Config
import io.beatmaps.common.formatTime
import io.beatmaps.common.toFixed
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.datetime.Clock
import kotlinx.html.title
import org.w3c.dom.events.Event
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
import react.dom.*
import react.setState

data class UserListState(var users: List<UserDetail> = listOf(), var loading: Boolean = false, var page: Int = 0) : RState

@JsExport
class UserList : RComponent<RProps, UserListState>() {

    init {
        state = UserListState()
    }

    override fun componentDidMount() {
        window.onscroll = ::handleScroll

        loadNextPage()
    }

    private fun loadNextPage() {
        if (state.loading)
            return

        setState {
            loading = true
        }

        axiosGet<Array<UserDetail>>("/api/users/list/${state.page}").then {
            setState {
                page++
                loading = it.data.isEmpty()
                users = state.users.plus(it.data)
            }
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
        val bodyHeight = document.body?.offsetHeight ?: 0
        val headerSize = 55
        val trigger = 300

        if (bodyHeight - (scrollPosition + windowSize) + headerSize < trigger) {
            loadNextPage()
        }
    }

    override fun RBuilder.render() {
        table("table table-dark mappers") {
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
                state.users.forEachIndexed { idx, u ->
                    if (u.stats == null) return@forEachIndexed

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
                            a("/profile/${u.id}") {
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
                            val total = ((u.stats.totalUpvotes + u.stats.totalDownvotes) * 0.01f) // 40.95
                            +"${(u.stats.totalUpvotes / if (total < 0.01f) 0.01f else total).toFixed(2)}%"
                        }
                        td {
                            +"${u.stats.totalMaps}"
                        }
                        td {
                            +"${u.stats.rankedMaps}"
                        }
                        td {
                            +Moment(u.stats.firstUpload.toString()).format("YYYY-MM-DD")
                        }
                        td {
                            +Moment(u.stats.lastUpload.toString()).format("YYYY-MM-DD")
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
            }
        }
    }
}

fun RBuilder.userList(handler: RProps.() -> Unit): ReactElement {
    return child(UserList::class) {
        this.attrs(handler)
    }
}