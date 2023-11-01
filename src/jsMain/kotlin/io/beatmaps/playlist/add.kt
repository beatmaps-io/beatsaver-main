package io.beatmaps.playlist

import external.Axios
import external.axiosGet
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.InPlaylist
import io.beatmaps.api.MapDetail
import io.beatmaps.api.PlaylistMapRequest
import io.beatmaps.index.ModalButton
import io.beatmaps.index.ModalData
import io.beatmaps.index.modalContext
import io.beatmaps.util.await
import io.beatmaps.util.launch
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import react.Props
import react.dom.a
import react.dom.i
import react.fc
import react.useContext
import react.useState

external interface AddToPlaylistProps : Props {
    var map: MapDetail
}

val addToPlaylist = fc<AddToPlaylistProps> { props ->
    val (loading, setLoading) = useState(false)

    val modal = useContext(modalContext)

    fun save(mapId: String, data: MutableMap<Int, Boolean>) {
        launch {
            data.forEach {
                Axios.post<String>(
                    "${Config.apibase}/playlists/id/${it.key}/add",
                    PlaylistMapRequest(mapId, it.value),
                    generateConfig<PlaylistMapRequest, String>()
                ).await()
            }
        }
    }

    fun openDialog() {
        if (loading) return
        setLoading(true)

        axiosGet<Array<InPlaylist>>("${Config.apibase}/maps/id/${props.map.id}/playlists").then { res ->
            setLoading(false)

            val changes: MutableMap<Int, Boolean> = mutableMapOf()
            modal?.current?.showDialog(
                ModalData(
                    "Add to playlist",
                    bodyCallback = {
                        addModal {
                            attrs.inPlaylists = res.data
                            attrs.changes = changes
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

    a("#") {
        attrs.onClickFunction = {
            it.preventDefault()
            openDialog()
        }

        attrs.title = "Add to playlist"
        attrs.attributes["aria-label"] = "Add to playlist"
        i("fas fa-plus text-success") {
            attrs.attributes["aria-hidden"] = "true"
        }
    }
}
