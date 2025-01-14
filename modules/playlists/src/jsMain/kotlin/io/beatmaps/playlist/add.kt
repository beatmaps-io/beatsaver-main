package io.beatmaps.playlist

import external.Axios
import external.axiosGet
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.InPlaylist
import io.beatmaps.api.PlaylistMapRequest
import io.beatmaps.shared.ModalButton
import io.beatmaps.shared.ModalData
import io.beatmaps.shared.modalContext
import io.beatmaps.util.await
import io.beatmaps.util.launch
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import react.dom.a
import react.dom.i
import react.dom.span
import react.fc
import react.useContext
import react.useState
import kotlin.js.Promise

val addToPlaylist = fc<AddToPlaylistProps>("addToPlaylist") { props ->
    val (loading, setLoading) = useState(false)

    val modal = useContext(modalContext)

    fun save(mapId: String, data: MutableMap<Int, Boolean>) = Promise.resolve(true).also {
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
            modal?.current?.showDialog?.invoke(
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

        val text = "Add to playlist"
        attrs.title = text
        attrs.attributes["aria-label"] = text
        span("dd-text") { +text }
        i("fas fa-plus text-success") {
            attrs.attributes["aria-hidden"] = "true"
        }
    }
}
