package io.beatmaps.api

enum class SearchOrder(val idx: Int) {
    Latest(0), Relevance(1), Rating(2);

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