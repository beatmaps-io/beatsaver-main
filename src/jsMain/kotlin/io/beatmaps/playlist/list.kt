package io.beatmaps.playlist

import external.Axios
import external.generateConfig
import io.beatmaps.api.PlaylistBasic
import io.beatmaps.common.Config
import kotlinx.browser.window
import kotlinx.html.ThScope
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import kotlinx.html.visible
import org.w3c.dom.events.Event
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
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
import react.setState
import kotlin.math.ceil
import kotlin.math.max

external interface UserPlaylistsProps : RProps {
    var userId: Int
    var own: Boolean?
    var history: RouteResultHistory
    var visible: Boolean?
}

external interface UserPlaylistsState : RState {
    var pages: Map<Int, List<PlaylistBasic>>
    var loading: Boolean?
    var visItem: Int
    var visPage: Int
    var visiblePages: IntRange
}

@JsExport
class UserPlaylists : RComponent<UserPlaylistsProps, UserPlaylistsState>() {
    private val playlistsPerPage = 20
    private val rowHeight = 73.0
    private val pageHeight = rowHeight * playlistsPerPage

    override fun componentWillMount() {
        val totalVisiblePages = ceil(window.innerHeight / pageHeight).toInt()
        setState {
            pages = mapOf()
            loading = false
            visItem = 0
            visPage = 0
            visiblePages = visPage.rangeTo(visPage + totalVisiblePages)
        }
    }

    override fun componentDidMount() {
        loadNextPage()
    }

    private fun loadNextPage() {
        if (state.loading == true)
            return

        val toLoad = state.visiblePages.firstOrNull { !state.pages.containsKey(it) } ?: return

        setState {
            loading = true
        }

        Axios.get<List<PlaylistBasic>>(
            "${Config.apibase}/playlists/user/${props.userId}/$toLoad",
            generateConfig<String, List<PlaylistBasic>>()
        ).then {
            setState {
                loading = it.data.isEmpty()
                pages = pages.plus(toLoad to it.data.toList())
            }
            window.onscroll = ::handleScroll
            if (it.data.isNotEmpty()) {
                window.setTimeout(::handleScroll, 1)
            }
        }.catch {
            // Oh noes
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
                visPage = item / playlistsPerPage
                visiblePages = visPage.rangeTo(visPage + totalVisiblePages)
            }
        }

        loadNextPage()
    }

    private fun lastPage() = max(state.visiblePages.last, state.pages.maxByOrNull { it.key }?.key ?: 0)

    override fun RBuilder.render() {
        if (props.visible == false) return

        table("table table-striped table-hover table-dark playlist") {
            thead {
                tr {
                    th(scope = ThScope.col) { i("fas fa-image") { attrs.title = "Cover" } }
                    th(scope = ThScope.col) {
                        attrs.colSpan = "2"
                        +"Name"
                        if (props.own == true) {
                            routeLink("/playlists/new", className = "btn btn-success btn-sm float-end") {
                                +"Create New"
                            }
                        }
                    }
                }
            }
            tbody {
                for (pIdx in 0..lastPage()) {
                    state.pages[pIdx]?.let {
                        it.forEach { pl ->
                            tr {
                                attrs.onClickFunction = { ev ->
                                    ev.preventDefault()
                                    props.history.push("/playlists/${pl.playlistId}")
                                }
                                td {
                                    img("Cover", pl.playlistImage) { }
                                }
                                td {
                                    +pl.name
                                }
                                td {
                                    if (!pl.public) {
                                        i("fas fa-lock") { attrs.title = "Private" }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun RBuilder.userPlaylists(handler: UserPlaylistsProps.() -> Unit): ReactElement {
    return child(UserPlaylists::class) {
        this.attrs(handler)
    }
}
