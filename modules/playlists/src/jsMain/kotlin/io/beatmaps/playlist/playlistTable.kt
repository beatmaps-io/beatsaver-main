package io.beatmaps.playlist

import external.Axios
import external.CancelTokenSource
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.GenericSearchResponse
import io.beatmaps.api.PlaylistFull
import io.beatmaps.api.PlaylistSearchResponse
import io.beatmaps.configContext
import io.beatmaps.shared.InfiniteScrollElementRenderer
import io.beatmaps.shared.generateInfiniteScrollComponent
import io.beatmaps.util.encodeURIComponent
import io.beatmaps.util.fcmemo
import io.beatmaps.util.useDidUpdateEffect
import react.dom.html.ReactHTML.div
import react.use
import react.useMemo
import react.useRef
import web.cssom.ClassName
import web.html.HTMLElement
import kotlin.js.Promise

val playlistTable = fcmemo<PlaylistTableProps>("playlistTable") { props ->
    val resetRef = useRef<() -> Unit>()
    val loadPageRef = useRef<(Int, CancelTokenSource) -> Promise<GenericSearchResponse<PlaylistFull>?>>()
    val config = use(configContext)

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
                playlist = pl
                small = props.small
            }
        }
    }

    if (props.visible != false) {
        div {
            className = ClassName("search-results")
            ref = resultsTable
            key = "resultsTable"

            playlistInfiniteScroll {
                this.resetRef = resetRef
                rowHeight = 80.0
                itemsPerPage = 20
                container = resultsTable
                renderElement = renderer
                updateScrollIndex = props.updateScrollIndex
                loadPage = loadPageRef
            }
        }
    }
}

val playlistInfiniteScroll = generateInfiniteScrollComponent(PlaylistFull::class)
