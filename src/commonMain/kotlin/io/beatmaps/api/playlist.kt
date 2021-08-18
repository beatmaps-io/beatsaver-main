package io.beatmaps.api

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
