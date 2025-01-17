package io.beatmaps.playlist

import external.routeLink
import io.beatmaps.api.InPlaylist
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.fc
import web.cssom.ClassName
import web.html.InputType

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
        div {
            attrs.className = ClassName("form-check mb-2")
            val id = "in-playlist-${ip.playlist.playlistId}"
            input {
                attrs.type = InputType.checkbox
                attrs.className = ClassName("form-check-input")
                attrs.id = id
                attrs.defaultChecked = ip.inPlaylist
                attrs.onChange = {
                    val current = it.currentTarget.checked

                    if (ip.inPlaylist == current) {
                        props.changes.remove(ip.playlist.playlistId)
                    } else {
                        props.changes[ip.playlist.playlistId] = current
                    }
                }
            }
            label {
                attrs.className = ClassName("w-100 form-check-label")
                attrs.htmlFor = id
                +ip.playlist.name
            }
        }
    }
}
