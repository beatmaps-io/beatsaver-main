package io.beatmaps.playlist

import external.Axios
import external.axiosGet
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.InPlaylist
import io.beatmaps.api.PlaylistMapRequest
import io.beatmaps.globalContext
import io.beatmaps.shared.ModalButton
import io.beatmaps.shared.ModalData
import io.beatmaps.shared.modalContext
import io.beatmaps.util.fcmemo
import io.beatmaps.util.get
import io.beatmaps.util.set
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.span
import react.use
import react.useState
import web.cssom.ClassName
import web.storage.localStorage
import kotlin.js.Promise

private const val MAX_RECENT_PLAYLISTS = 5

private fun recentPlaylistsKey(userId: Int) =
    "beatsaver:recentPlaylists:$userId"

fun getRecentPlaylistIds(userId: Int?): List<Int> {
    if (userId == null) return emptyList()

    return try {
        localStorage[recentPlaylistsKey(userId)]
            ?.split(",")
            ?.mapNotNull { it.toIntOrNull() }
            ?.distinct()
            ?.take(MAX_RECENT_PLAYLISTS)
            ?: emptyList()
    } catch (_: Throwable) {
        emptyList()
    }
}

private fun rememberRecentPlaylists(
    userId: Int?,
    playlistIds: List<Int>
) {
    if (userId == null || playlistIds.isEmpty()) return

    try {
        val recent = (playlistIds.reversed() + getRecentPlaylistIds(userId))
            .distinct().take(MAX_RECENT_PLAYLISTS)

        localStorage[recentPlaylistsKey(userId)] =
            recent.joinToString(",")
    } catch (_: Throwable) {
        // Playlist saving should still work if storage is unavailable
    }
}

val addToPlaylist = fcmemo<AddToPlaylistProps>("addToPlaylist") { props ->
    val (loading, setLoading) = useState(false)

    val modal = use(modalContext)
    val userId = use(globalContext)?.userId

    fun save(mapId: String, data: MutableMap<Int, Boolean>): Promise<Boolean> {
        if (data.isEmpty()) return Promise.resolve(true)

        val changes = data.toMap()

        val requests = changes.map { (playlistId, inPlaylist) ->
            Axios.post<String>(
                "${Config.apibase}/playlists/id/$playlistId/add",
                PlaylistMapRequest(mapId, inPlaylist),
                generateConfig<PlaylistMapRequest, String>()
            )
        }.toTypedArray()

        return Promise.all(requests).then({
            rememberRecentPlaylists(userId, changes.keys.toList())
            true
        }, { error ->
            console.error("Failed to update playlists", error)
            false
        })
    }

    fun openDialog() {
        if (loading) return
        setLoading(true)

        axiosGet<Array<InPlaylist>>("${Config.apibase}/maps/id/${props.map.id}/playlists").then { res ->
            setLoading(false)

            val changes: MutableMap<Int, Boolean> = mutableMapOf()
            modal?.current?.showDialog?.invoke(
                ModalData(
                    "Add to playlist",
                    bodyCallback = {
                        addModal {
                            inPlaylists = res.data
                            this.changes = changes
                        }
                    },
                    buttons = listOf(
                        ModalButton("Save", "primary") {
                            save(props.map.id, changes)
                        },
                        ModalButton("Cancel")
                    )
                )
            )
        }.catch {
            setLoading(false)
        }
    }

    a {
        href = "#"

        onClick = {
            it.preventDefault()
            openDialog()
        }

        val text = "Add to playlist"
        title = text
        ariaLabel = text
        span {
            className = ClassName("dd-text")
            +text
        }
        i {
            className = ClassName("fas fa-plus text-success")
            ariaHidden = true
        }
    }
}
