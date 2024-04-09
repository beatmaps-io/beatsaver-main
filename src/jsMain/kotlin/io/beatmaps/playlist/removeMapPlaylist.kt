package io.beatmaps.playlist

import external.Axios
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.MapDetail
import io.beatmaps.api.PlaylistMapRequest
import io.beatmaps.index.beatmapInfo
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import org.w3c.dom.Audio
import react.MutableRefObject
import react.Props
import react.dom.a
import react.dom.div
import react.dom.i
import react.fc

external interface RemoveMapPlaylistProps : Props {
    var obj: MapDetail
    var audio: MutableRefObject<Audio>
    var playlistKey: Int
    var removeMap: (() -> Unit)?
}

var removeMapPlaylist = fc<RemoveMapPlaylistProps> { props ->
    fun remove() {
        Axios.post<String>(
            "${Config.apibase}/playlists/id/${props.playlistKey}/add",
            PlaylistMapRequest(props.obj.id, false),
            generateConfig<PlaylistMapRequest, String>()
        )
        props.removeMap?.invoke()
    }

    val classes = "del-beatmap"

    div(classes) {
        beatmapInfo {
            obj = props.obj
            version = props.obj.publishedVersion()
            this.audio = props.audio
        }
        div("del-beatmap-btn-cell") {
            a("#", classes = "btn btn-danger") {
                attrs.onClickFunction = {
                    it.preventDefault()
                    remove()
                }
                val title = "Remove from playlist"
                attrs.title = title
                attrs.attributes["aria-label"] = title
                i("fa fa-times") { }
            }
        }
    }
}
