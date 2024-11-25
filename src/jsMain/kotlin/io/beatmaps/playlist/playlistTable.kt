package io.beatmaps.playlist

import external.Axios
import external.CancelTokenSource
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.PlaylistFull
import io.beatmaps.api.PlaylistSearchResponse
import io.beatmaps.common.SearchOrder
import io.beatmaps.configContext
import io.beatmaps.shared.InfiniteScroll
import io.beatmaps.shared.InfiniteScrollElementRenderer
import io.beatmaps.shared.search.CommonParams
import io.beatmaps.util.encodeURIComponent
import io.beatmaps.util.useDidUpdateEffect
import org.w3c.dom.HTMLElement
import react.Props
import react.dom.div
import react.fc
import react.useContext
import react.useRef
import react.useState

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
    var visible: Boolean?
    var updateScrollIndex: ((Int) -> Unit)?
}

val playlistTable = fc<PlaylistTableProps> { props ->
    val (resultsKey, setResultsKey) = useState(Any())
    val config = useContext(configContext)

    val resultsTable = useRef<HTMLElement>()

    useDidUpdateEffect(props.userId, props.search) {
        setResultsKey(Any())
    }

    fun getUrl(page: Int) =
        if (props.userId != null) {
            "${Config.apibase}/playlists/user/${props.userId}/$page"
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

    val loadPage = { toLoad: Int, token: CancelTokenSource ->
        Axios.get<PlaylistSearchResponse>(
            getUrl(toLoad),
            generateConfig<String, PlaylistSearchResponse>(token.token)
        ).then {
            return@then it.data.docs
        }
    }

    if (props.visible != false) {
        div("search-results") {
            ref = resultsTable
            key = "resultsTable"

            child(PlaylistInfiniteScroll::class) {
                attrs.resultsKey = resultsKey
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
