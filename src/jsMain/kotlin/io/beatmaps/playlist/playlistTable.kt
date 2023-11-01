package io.beatmaps.playlist

import external.Axios
import external.CancelTokenSource
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.History
import io.beatmaps.api.PlaylistFull
import io.beatmaps.api.PlaylistSearchResponse
import io.beatmaps.common.SearchOrder
import io.beatmaps.shared.InfiniteScroll
import io.beatmaps.shared.InfiniteScrollElementRenderer
import io.beatmaps.shared.search.CommonParams
import io.beatmaps.util.encodeURIComponent
import org.w3c.dom.HTMLElement
import react.Props
import react.RBuilder
import react.RComponent
import react.State
import react.createRef
import react.dom.div

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
) : CommonParams()

external interface PlaylistTableProps : Props {
    var search: PlaylistSearchParams?
    var userId: Int?
    var own: Boolean?
    var history: History
    var visible: Boolean?
    var updateScrollIndex: ((Int) -> Unit)?
}

external interface PlaylistTableState : State {
    var resultsKey: Any
}

class PlaylistTable : RComponent<PlaylistTableProps, PlaylistTableState>() {
    private val resultsTable = createRef<HTMLElement>()

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
                    (if (search.includeEmpty != null) "&includeEmpty=${search.includeEmpty}" else "")
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
                        obj = pl
                    }
                }
                attrs.updateScrollIndex = props.updateScrollIndex
                attrs.loadPage = loadPage
            }
        }
    }
}

class PlaylistInfiniteScroll : InfiniteScroll<PlaylistFull>()

fun RBuilder.playlistTable(handler: PlaylistTableProps.() -> Unit) =
    child(PlaylistTable::class) {
        this.attrs(handler)
    }
