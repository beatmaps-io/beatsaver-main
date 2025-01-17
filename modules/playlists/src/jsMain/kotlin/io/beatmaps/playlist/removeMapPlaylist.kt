package io.beatmaps.playlist

import external.Axios
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.MapDetail
import io.beatmaps.api.PlaylistMapRequest
import io.beatmaps.index.beatmapInfo
import io.beatmaps.util.fcmemo
import org.w3c.dom.Audio
import react.Props
import react.RefObject
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import web.cssom.ClassName

external interface PlaylistMapEditableProps : Props {
    var obj: MapDetail
    var audio: RefObject<Audio>
    var playlistKey: Int
    var removeMap: (() -> Unit)?
}

var playlistMapEditable = fcmemo<PlaylistMapEditableProps>("playlistMapEditable") { props ->
    fun remove() {
        Axios.post<String>(
            "${Config.apibase}/playlists/id/${props.playlistKey}/add",
            PlaylistMapRequest(props.obj.id, false),
            generateConfig<PlaylistMapRequest, String>()
        )
        props.removeMap?.invoke()
    }

    div {
        attrs.className = ClassName("playlist-map")
        i {
            attrs.className = ClassName("fas fa-grip-lines-vertical")
        }
        beatmapInfo {
            attrs.obj = props.obj
            attrs.version = props.obj.publishedVersion()
            attrs.audio = props.audio
        }
        div {
            attrs.className = ClassName("delete")
            a {
                attrs.href = "#"
                attrs.className = ClassName("btn btn-danger")
                attrs.onClick = {
                    it.preventDefault()
                    remove()
                }
                val title = "Remove from playlist"
                attrs.title = title
                attrs.ariaLabel = title
                i {
                    attrs.className = ClassName("fa fa-times")
                }
            }
        }
    }
}
