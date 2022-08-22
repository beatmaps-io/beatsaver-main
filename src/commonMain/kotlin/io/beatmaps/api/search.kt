// ktlint-disable filename
package io.beatmaps.api

enum class SortOrderTarget {
    Map, Playlist;

    companion object {
        val all = values().toList()
    }
}

enum class SearchOrder(val idx: Int, val targets: List<SortOrderTarget>) {
    Latest(0, SortOrderTarget.all),
    Relevance(1, SortOrderTarget.all),
    Rating(2, listOf(SortOrderTarget.Map)),
    Curated(3, SortOrderTarget.all);

    companion object {
        private val map = values().associateBy(SearchOrder::idx)
        fun fromInt(type: Int) = map[type]

        fun fromString(str: String?) = try {
            valueOf(str ?: "")
        } catch (e: Exception) {
            null
        }
    }
}
