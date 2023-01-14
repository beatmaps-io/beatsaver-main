package io.beatmaps.api

import io.beatmaps.common.api.EPlaylistType
import io.beatmaps.common.fixed
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
data class PlaylistBasic(val playlistId: Int, val playlistImage: String, val name: String, val type: EPlaylistType, val owner: Int)

@Serializable
data class PlaylistFull(
    val playlistId: Int,
    val name: String,
    val description: String,
    val playlistImage: String,
    val playlistImage512: String? = null,
    val owner: UserDetail,
    val curator: UserDetail? = null,
    val stats: PlaylistStats? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val songsChangedAt: Instant?,
    val curatedAt: Instant? = null,
    val deletedAt: Instant? = null,
    val downloadURL: String,
    val type: EPlaylistType
) {
    companion object
}

@Serializable
data class PlaylistStats(
    val totalMaps: Int,
    val mapperCount: Long,
    val totalDuration: Int,
    val minNps: Double,
    val maxNps: Double,
    val upVotes: Int,
    val downVotes: Int,
    val avgScore: Float
) {
    val scoreOneDP by lazy { (avgScore * 100).fixed(1) }
    val minNpsTwoDP by lazy { minNps.fixed(2) }
    val maxNpsTwoDP by lazy { maxNps.fixed(2) }
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
@Serializable
data class CuratePlaylist(val id: Int, val curated: Boolean = false)

@Serializable
data class BookmarkRequest(val key: String? = null, val hash: String? = null, val bookmarked: Boolean)

@Serializable
data class BookmarkUpdateResponse(val updated: Boolean)

@Serializable
data class BookmarkResponse(val docs: List<MapDetail>)
