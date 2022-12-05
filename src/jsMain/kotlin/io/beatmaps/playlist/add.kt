package io.beatmaps.playlist

import external.Axios
import external.axiosGet
import external.generateConfig
import external.reactFor
import external.routeLink
import io.beatmaps.api.InPlaylist
import io.beatmaps.api.MapDetail
import io.beatmaps.api.PlaylistMapRequest
import io.beatmaps.common.Config
import io.beatmaps.index.ModalButton
import io.beatmaps.index.ModalComponent
import io.beatmaps.index.ModalData
import kotlinx.html.InputType
import kotlinx.html.id
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import org.w3c.dom.HTMLInputElement
import react.Props
import react.RBuilder
import react.RComponent
import react.RefObject
import react.State
import react.dom.a
import react.dom.div
import react.dom.i
import react.dom.input
import react.dom.label
import react.functionComponent
import react.setState
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import kotlin.js.Promise

external interface AddToPlaylistProps : Props {
    var map: MapDetail
    var modal: RefObject<ModalComponent>
}

external interface AddToPlaylistState : State {
    var loading: Boolean?
}

suspend fun <T> Promise<T>.await(): T = suspendCoroutine { cont ->
    then({ cont.resume(it) }, { cont.resumeWithException(it) })
}

fun launch(block: suspend () -> Unit) {
    block.startCoroutine(object : Continuation<Unit> {
        override val context: CoroutineContext get() = EmptyCoroutineContext
        override fun resumeWith(result: Result<Unit>) {
            if (result.isFailure) {
                console.log("Coroutine failed: ${result.exceptionOrNull()}")
            }
        }
    })
}

external interface AddModalProps : Props {
    var changes: MutableMap<Int, Boolean>
    var inPlaylists: Array<InPlaylist>
}

class AddToPlaylist : RComponent<AddToPlaylistProps, AddToPlaylistState>() {
    private fun save(mapId: String, data: MutableMap<Int, Boolean>) {
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

    val addModal = functionComponent<AddModalProps> { props ->
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

    private fun openDialog() {
        if (state.loading == true) return

        setState {
            loading = true
        }

        axiosGet<Array<InPlaylist>>("${Config.apibase}/maps/id/${props.map.id}/playlists").then { res ->
            setState {
                loading = false
            }

            val changes: MutableMap<Int, Boolean> = mutableMapOf()
            props.modal.current?.showDialog(
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
            setState {
                loading = false
            }
        }
    }

    override fun RBuilder.render() {
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
}

fun RBuilder.addToPlaylist(handler: AddToPlaylistProps.() -> Unit) =
    child(AddToPlaylist::class) {
        this.attrs(handler)
    }
