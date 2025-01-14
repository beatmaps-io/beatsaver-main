package io.beatmaps.api

actual object LinkHelper {
    actual fun profileLink(userDetail: UserDetail, tab: String?, absolute: Boolean) = "/profile/${userDetail.id}" + (tab?.let { "#$it" } ?: "")
    actual fun mapLink(mapDetail: MapDetail, absolute: Boolean) = "/maps/${mapDetail.id}"
    actual fun playlistLink(playlist: PlaylistFull, absolute: Boolean) = "/playlists/${playlist.playlistId}"
}
