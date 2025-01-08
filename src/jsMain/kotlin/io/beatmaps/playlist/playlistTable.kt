package io.beatmaps.playlist

import external.Axios
import external.CancelTokenSource
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.GenericSearchResponse
import io.beatmaps.api.PlaylistFull
import io.beatmaps.api.PlaylistSearchResponse
import io.beatmaps.common.SearchOrder
import io.beatmaps.configContext
import io.beatmaps.shared.InfiniteScrollElementRenderer
import io.beatmaps.shared.generateInfiniteScrollComponent
import io.beatmaps.shared.search.CommonParams
import io.beatmaps.util.encodeURIComponent
import io.beatmaps.util.useDidUpdateEffect
import org.w3c.dom.HTMLElement
import react.Props
import react.RefObject
import react.dom.div
import react.fc
import react.useContext
import react.useMemo
import react.useRef
import kotlin.js.Promise

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
    var mapId: String?
    var small: Boolean?
    var visible: Boolean?
    var updateScrollIndex: RefObject<(Int) -> Unit>
}

val playlistTable = fc<PlaylistTableProps>("playlistTable") { props ->
    val resetRef = useRef<() -> Unit>()
    val loadPageRef = useRef<(Int, CancelTokenSource) -> Promise<GenericSearchResponse<PlaylistFull>?>>()
    val config = useContext(configContext)

    val resultsTable = useRef<HTMLElement>()

    useDidUpdateEffect(props.userId, props.search) {
        resetRef.current?.invoke()
    }

    fun getUrl(page: Int) =
        if (props.userId != null) {
            "${Config.apibase}/playlists/user/${props.userId}/$page"
        } else if (props.mapId != null) {
            "${Config.apibase}/playlists/map/${props.mapId}/$page"
        } else {
            props.search?.let { search ->
                "${Config.apibase}/playlists/search${if (config?.v2Search == true) "" else "/v1"}/$page?sortOrder=${search.sortOrder}" +
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

    loadPageRef.current = { toLoad: Int, token: CancelTokenSource ->
        Axios.get<PlaylistSearchResponse>(
            getUrl(toLoad),
            generateConfig<String, PlaylistSearchResponse>(token.token)
        ).then {
            return@then it.data
        }
    }

    val renderer = useMemo(props.small) {
        InfiniteScrollElementRenderer<PlaylistFull> { pl ->
            playlistInfo {
                attrs.playlist = pl
                attrs.small = props.small
            }
        }
    }

    if (props.visible != false) {
        div("search-results") {
            ref = resultsTable
            key = "resultsTable"

            playlistInfiniteScroll {
                attrs.resetRef = resetRef
                attrs.rowHeight = 80.0
                attrs.itemsPerPage = 20
                attrs.container = resultsTable
                attrs.renderElement = renderer
                attrs.updateScrollIndex = props.updateScrollIndex
                attrs.loadPage = loadPageRef
            }
        }
    }
}

val playlistInfiniteScroll = generateInfiniteScrollComponent(PlaylistFull::class)
