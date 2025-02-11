package io.beatmaps.api

import io.beatmaps.common.Config

actual object LinkHelper {
    actual fun profileLink(userDetail: UserDetail, tab: String?, absolute: Boolean) = Config.siteBase(absolute) + "/profile/${userDetail.id}" + (tab?.let { "#$it" } ?: "")
    actual fun mapLink(mapDetail: MapDetail, absolute: Boolean) = Config.siteBase(absolute) + "/maps/${mapDetail.id}"
    actual fun playlistLink(playlist: PlaylistFull, absolute: Boolean) = Config.siteBase(absolute) + "/playlists/${playlist.playlistId}"
}
