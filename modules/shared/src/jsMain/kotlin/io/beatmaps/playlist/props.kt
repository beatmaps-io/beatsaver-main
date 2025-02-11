package io.beatmaps.playlist

import io.beatmaps.api.MapDetail
import io.beatmaps.api.PlaylistFull
import io.beatmaps.common.SearchOrder
import io.beatmaps.shared.search.CommonParams
import io.beatmaps.util.includeIfNotNull
import react.Props
import react.RefObject

external interface AddToPlaylistProps : Props {
    var map: MapDetail
}

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
) : CommonParams() {
    override fun queryParams() = super.queryParams() + listOfNotNull(
        includeIfNotNull(curated, "curated"),
        includeIfNotNull(verified, "verified")
    )
}

external interface PlaylistTableProps : Props {
    var search: PlaylistSearchParams?
    var userId: Int?
    var mapId: String?
    var small: Boolean?
    var visible: Boolean?
    var updateScrollIndex: RefObject<(Int) -> Unit>
}

external interface PlaylistInfoProps : Props {
    var playlist: PlaylistFull?
    var small: Boolean?
}
