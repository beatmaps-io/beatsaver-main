package io.beatmaps.playlist

import external.reactFor
import external.routeLink
import io.beatmaps.api.InPlaylist
import kotlinx.html.InputType
import kotlinx.html.id
import kotlinx.html.js.onChangeFunction
import org.w3c.dom.HTMLInputElement
import react.Props
import react.dom.div
import react.dom.input
import react.dom.label
import react.fc

external interface AddModalProps : Props {
    var changes: MutableMap<Int, Boolean>
    var inPlaylists: Array<InPlaylist>
}

val addModal = fc<AddModalProps>("addModal") { props ->
    if (props.inPlaylists.isEmpty()) {
        div {
            +"You don't have any playlists yet!"
        }
        routeLink("/playlists/new", className = "btn btn-success btn-sm mt-2") {
            +"Create New"
        }
    }
    props.inPlaylists.map { ip ->
        div("form-check mb-2") {
            val id = "in-playlist-${ip.playlist.playlistId}"
            input(InputType.checkBox, classes = "form-check-input") {
                attrs.id = id
                attrs.defaultChecked = ip.inPlaylist
                attrs.onChangeFunction = {
                    val current = (it.currentTarget as HTMLInputElement).checked

                    if (ip.inPlaylist == current) {
                        props.changes.remove(ip.playlist.playlistId)
                    } else {
                        props.changes[ip.playlist.playlistId] = current
                    }
                }
            }
            label("w-100 form-check-label") {
                attrs.reactFor = id
                +ip.playlist.name
            }
        }
    }
}
