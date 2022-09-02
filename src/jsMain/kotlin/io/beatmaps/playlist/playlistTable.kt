package io.beatmaps.playlist

import external.Axios
import external.CancelTokenSource
import external.generateConfig
import io.beatmaps.api.PlaylistFull
import io.beatmaps.api.PlaylistSearchResponse
import io.beatmaps.api.SearchOrder
import io.beatmaps.common.Config
import io.beatmaps.index.encodeURIComponent
import io.beatmaps.shared.CommonParams
import io.beatmaps.shared.InfiniteScroll
import io.beatmaps.shared.InfiniteScrollElementRenderer
import org.w3c.dom.HTMLDivElement
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
import react.createRef
import react.dom.div
import react.router.dom.RouteResultHistory

data class PlaylistSearchParams(
    override val search: String,
    override val minNps: Float?,
    override val maxNps: Float?,
    override val from: String? = null,
    override val to: String? = null,
    val includeEmpty: Boolean? = null,
    val curated: Boolean? = null,
    val verified: Boolean? = null,
    override val sortOrder: SearchOrder
) : CommonParams

external interface PlaylistTableProps : RProps {
    var search: PlaylistSearchParams?
    var userId: Int?
    var own: Boolean?
    var history: RouteResultHistory
    var visible: Boolean?
    var updateScrollIndex: ((Int) -> Unit)?
}

external interface PlaylistTableState : RState {
    var resultsKey: Any
}

class PlaylistTable : RComponent<PlaylistTableProps, PlaylistTableState>() {
    private val resultsTable = createRef<HTMLDivElement>()

    override fun componentWillUpdate(nextProps: PlaylistTableProps, nextState: PlaylistTableState) {
        if (props.userId != nextProps.userId || props.search !== nextProps.search) {
            nextState.apply {
                resultsKey = Any()
            }
        }
    }

    private fun getUrl(page: Int) =
        if (props.userId != null) {
            "${Config.apibase}/playlists/user/${props.userId}/$page"
        } else {
            props.search?.let { search ->
                "${Config.apibase}/playlists/search/$page?sortOrder=${search.sortOrder}" +
                    (if (search.curated != null) "&curated=${search.curated}" else "") +
                    (if (search.verified != null) "&verified=${search.verified}" else "") +
                    (if (search.search.isNotBlank()) "&q=${encodeURIComponent(search.search)}" else "") +
                    (if (search.maxNps != null) "&maxNps=${search.maxNps}" else "") +
                    (if (search.minNps != null) "&minNps=${search.minNps}" else "") +
                    (if (search.from != null) "&from=${search.from}" else "") +
                    (if (search.to != null) "&to=${search.to}" else "") +
                    (if (search.includeEmpty != null) "&to=${search.includeEmpty}" else "")
            } ?: ""
        }

    private val loadPage = { toLoad: Int, token: CancelTokenSource ->
        Axios.get<PlaylistSearchResponse>(
            getUrl(toLoad),
            generateConfig<String, PlaylistSearchResponse>(token.token)
        ).then {
            return@then it.data.docs
        }
    }

    override fun RBuilder.render() {
        if (props.visible == false) return

        div("search-results") {
            ref = resultsTable
            key = "resultsTable"

            child(PlaylistInfiniteScroll::class) {
                attrs.resultsKey = state.resultsKey
                attrs.rowHeight = 80.0
                attrs.itemsPerPage = 20
                attrs.container = resultsTable
                attrs.renderElement = InfiniteScrollElementRenderer { pl ->
                    playlistInfo {
                        playlist = pl
                    }
                }
                attrs.updateScrollIndex = props.updateScrollIndex
                attrs.loadPage = loadPage
            }
        }
    }
}

class PlaylistInfiniteScroll : InfiniteScroll<PlaylistFull>()

fun RBuilder.playlistTable(handler: PlaylistTableProps.() -> Unit): ReactElement {
    return child(PlaylistTable::class) {
        this.attrs(handler)
    }
}
