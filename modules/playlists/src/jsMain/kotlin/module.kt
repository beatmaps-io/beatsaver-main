import io.beatmaps.playlist.addToPlaylist
import io.beatmaps.playlist.editPlaylist
import io.beatmaps.playlist.multiAddPlaylist
import io.beatmaps.playlist.playlistFeed
import io.beatmaps.playlist.playlistInfo
import io.beatmaps.playlist.playlistPage
import io.beatmaps.playlist.playlistTable

@JsExport
val edit = editPlaylist

@JsExport
val multiAdd = multiAddPlaylist

@JsExport
val feed = playlistFeed

@JsExport
val page = playlistPage

@JsExport
val addTo = addToPlaylist

@JsExport
val table = playlistTable

@JsExport
val info = playlistInfo
