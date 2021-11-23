package io.beatmaps.api

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Playlist(
    val playlistTitle: String,
    val playlistAuthor: String,
    val playlistDescription: String,
    val image: String,
    val customData: PlaylistCustomData,
    val songs: List<PlaylistSong>
)

@Serializable
data class PlaylistSong(val key: String?, val hash: String, val songName: String)

@Serializable
data class PlaylistCustomData(val syncURL: String)

@Serializable
data class PlaylistBasic(val playlistId: Int, val playlistImage: String, val name: String, val public: Boolean, val owner: Int)

@Serializable
data class PlaylistFull(
    val playlistId: Int,
    val name: String,
    val description: String,
    val playlistImage: String,
    val public: Boolean,
    val owner: UserDetail?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val songsChangedAt: Instant?
) {
    companion object
}

@Serializable
data class InPlaylist(val playlist: PlaylistBasic, val inPlaylist: Boolean) {
    companion object
}

@Serializable
data class PlaylistMapRequest(val mapId: String, val inPlaylist: Boolean? = null, val order: Float? = null)

@Serializable
data class MapDetailWithOrder(val map: MapDetail, val order: Float)
@Serializable
data class PlaylistPage(val playlist: PlaylistFull? = null, val maps: List<MapDetailWithOrder>? = null)

@Serializable
data class PlaylistSearchResponse(val docs: List<PlaylistFull>)
