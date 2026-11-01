package io.beatmaps.playlist

import external.routeLink
import io.beatmaps.api.InPlaylist
import io.beatmaps.globalContext
import io.beatmaps.util.fcmemo
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.use
import web.cssom.ClassName
import web.html.InputType

external interface AddModalProps : Props {
    var changes: MutableMap<Int, Boolean>
    var inPlaylists: Array<InPlaylist>
}

val addModal = fcmemo<AddModalProps>("addModal") { props ->
    val recentIds = getRecentPlaylistIds(use(globalContext)?.userId)

    val recentlyUpdated = recentIds.mapNotNull { id ->
        props.inPlaylists.firstOrNull { it.playlist.playlistId == id }
    }

    val otherPlaylists = props.inPlaylists.filter {
        it.playlist.playlistId !in recentIds
    }

    if (props.inPlaylists.isEmpty()) {
        div {
            +"You don't have any playlists yet!"
        }
        routeLink("/playlists/new", className = "btn btn-success btn-sm mt-2") {
            +"Create New"
        }
    }

    val groups = if (recentlyUpdated.isEmpty()) {
        listOf("All playlists" to otherPlaylists)
    } else {
        listOf(
            "Recently updated" to recentlyUpdated,
            "All other playlists" to otherPlaylists
        )
    }

    groups.forEach { (heading, playlists) ->
        if (playlists.isNotEmpty()) {
            div {
                className = ClassName(
                    if (heading == "All other playlists") {
                        "fw-semibold border-top pt-3 mt-3 mb-2"
                    } else {
                        "fw-semibold mb-2"
                    }
                )
                +heading
            }

            playlists.forEach { ip ->
                div {
                    className = ClassName("form-check mb-2")
                    val id = "in-playlist-${ip.playlist.playlistId}"
                    input {
                        type = InputType.checkbox
                        className = ClassName("form-check-input")
                        this.id = id
                        defaultChecked = ip.inPlaylist
                        onChange = {
                            val current = it.currentTarget.checked

                            if (ip.inPlaylist == current) {
                                props.changes.remove(ip.playlist.playlistId)
                            } else {
                                props.changes[ip.playlist.playlistId] = current
                            }
                        }
                    }
                    label {
                        className = ClassName("w-100 form-check-label")
                        htmlFor = id
                        +ip.playlist.name
                    }
                }
            }
        }
    }
}
