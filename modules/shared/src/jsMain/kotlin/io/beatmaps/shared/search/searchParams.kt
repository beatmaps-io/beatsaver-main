package io.beatmaps.shared.search

import io.beatmaps.common.SearchOrder
import io.beatmaps.util.encodeURIComponent
import io.beatmaps.util.includeIfNotNull

abstract class CommonParams {
    abstract val search: String
    abstract val sortOrder: SearchOrder
    abstract val ascending: Boolean?
    abstract val minNps: Float?
    abstract val maxNps: Float?
    abstract val from: String?
    abstract val to: String?

    open fun queryParams() = listOfNotNull(
        (if (search.isNotBlank()) "q=${encodeURIComponent(search)}" else null),
        includeIfNotNull(maxNps, "maxNps"),
        includeIfNotNull(minNps, "minNps"),
        (if (sortOrder != SearchOrder.Relevance) "order=$sortOrder" else null),
        if (ascending == true) "ascending=true" else null,
        includeIfNotNull(from, "from"),
        includeIfNotNull(to, "to")
    ).toTypedArray()
}
