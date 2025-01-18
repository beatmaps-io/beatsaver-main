package io.beatmaps.playlist

import external.Axios
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.MapDetail
import io.beatmaps.api.PlaylistMapRequest
import io.beatmaps.index.beatmapInfo
import io.beatmaps.util.fcmemo
import react.Props
import react.RefObject
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import web.cssom.ClassName
import web.html.Audio

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
        className = ClassName("playlist-map")
        i {
            className = ClassName("fas fa-grip-lines-vertical")
        }
        beatmapInfo {
            obj = props.obj
            version = props.obj.publishedVersion()
            audio = props.audio
        }
        div {
            className = ClassName("delete")
            a {
                href = "#"
                className = ClassName("btn btn-danger")
                onClick = {
                    it.preventDefault()
                    remove()
                }
                val title = "Remove from playlist"
                this.title = title
                ariaLabel = title
                i {
                    className = ClassName("fa fa-times")
                }
            }
        }
    }
}
