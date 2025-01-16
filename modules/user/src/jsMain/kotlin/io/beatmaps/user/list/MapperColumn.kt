package io.beatmaps.user.list

import io.beatmaps.common.api.UserSearchSort

enum class MapperColumn(val text: String, val icon: String? = null, val width: Int? = null, val sortEnum: UserSearchSort? = null) {
    NUMERAL("#", width = 24),
    AVATAR("Avatar", "fas fa-image", width = 43),
    NAME("Mapper"),
    BPM("Avg BPM", "fa-tachometer-alt", width = 41, sortEnum = UserSearchSort.BPM),
    DURATION("Avg Duration", "fa-clock", width = 31, sortEnum = UserSearchSort.DURATION),
    UPVOTES("Total Upvotes", "fa-thumbs-up", width = 53, sortEnum = UserSearchSort.UPVOTES),
    DOWNVOTES("Total Downvotes", "fa-thumbs-down", width = 44, sortEnum = UserSearchSort.DOWNVOTES),
    RATIO("Ratio", "fa-percentage", width = 43, sortEnum = UserSearchSort.RATIO),
    MAPS("Total Maps", "fa-map-marked", width = 36, sortEnum = UserSearchSort.MAPS),
    RANKED("Ranked Maps", "fa-star", width = 29, sortEnum = UserSearchSort.RANKED_MAPS),
    FIRST("First", width = 73, sortEnum = UserSearchSort.FIRST_UPLOAD),
    LAST("Last", width = 73, sortEnum = UserSearchSort.LAST_UPLOAD),
    SINCE("Since", width = 36),
    AGE("Age", width = 31, sortEnum = UserSearchSort.MAP_AGE),
    PLAYLIST("Playlist", width = 56);

    companion object {
        private val map = entries.associateBy(MapperColumn::sortEnum)
        fun fromSort(sort: UserSearchSort) = map[sort]
    }
}
