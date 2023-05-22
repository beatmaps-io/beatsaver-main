package io.beatmaps.playlist

import external.Axios
import external.generateConfig
import external.reactFor
import external.routeLink
import io.beatmaps.Config
import io.beatmaps.WithRouterProps
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.Playlist
import io.beatmaps.api.PlaylistBatchRequest
import io.beatmaps.common.jsonIgnoreUnknown
import kotlinx.browser.window
import kotlinx.html.InputType
import kotlinx.html.id
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import kotlinx.html.role
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.files.FileReader
import org.w3c.files.get
import react.RBuilder
import react.RComponent
import react.State
import react.createRef
import react.dom.br
import react.dom.button
import react.dom.div
import react.dom.input
import react.dom.jsStyle
import react.dom.label
import react.dom.p
import react.dom.textarea
import react.setState

external interface MultiAddPlaylistProps : WithRouterProps

external interface MultiAddPlaylistState : State {
    var progress: Pair<Int, Int>?
}

class MultiAddPlaylist : RComponent<MultiAddPlaylistProps, MultiAddPlaylistState>() {
    private val hashRef = createRef<HTMLTextAreaElement>()
    private val bplistUploadRef = createRef<HTMLInputElement>()
    private val hashRegex = Regex("^[A-F0-9]{40}$", RegexOption.IGNORE_CASE)

    private fun startAdd(hashes: List<String>) {
        doAdd(hashes.chunked(50))
    }

    private fun doAdd(queue: List<List<String>>) {
        if (queue.isEmpty()) {
            if (state.progress?.first == state.progress?.second) {
                props.history.push("/playlists/${props.params["id"]}")
            }

            return
        }

        val hashes = queue.first()
        val (keyList, hashList) = hashes.partition { !hashRegex.matches(it) }

        Axios.post<ActionResponse>(
            "${Config.apibase}/playlists/id/${props.params["id"]}/batch",
            PlaylistBatchRequest(hashList, keyList, true),
            generateConfig<PlaylistBatchRequest, ActionResponse>()
        ).then {
            setState {
                progress = progress?.let { it.first + hashes.size to it.second }
            }
            window.setTimeout(
                {
                    doAdd(queue.minus(setOf(hashes)))
                },
                500
            )
        }.catch {
            console.log("doAdd catch", it)
            setState {
                progress = progress?.let { -1 to it.second }
            }
        }
    }

    override fun RBuilder.render() {
        val id = props.params["id"]

        div("card border-dark w-50 m-auto") {
            div("card-header") {
                +"Add maps to playlist"
            }
            div(classes = "card-body") {
                state.progress?.let { progress ->
                    if (progress.first >= 0) {
                        p("h4 text-center mt-4") {
                            +"Adding maps to playlist (${progress.first} / ${progress.second})"
                        }
                        div("progress m-4") {
                            div("progress-bar progress-bar-striped progress-bar-animated bg-info") {
                                attrs.role = "progressbar"
                                attrs.jsStyle {
                                    val v = ((progress.first * 100f) / progress.second).toInt()
                                    width = "$v%"
                                }
                            }
                        }
                    } else {
                        p("h4 text-center") {
                            +"Error adding maps to playlist"
                            br {}
                            +"Hashes/keys are invalid or the maps don't exist"
                        }
                        div("btn-group w-100 mt-5") {
                            routeLink(id?.let { "/playlists/$it" } ?: "/", className = "btn btn-secondary") {
                                +"Back"
                            }
                        }
                    }
                } ?: run {
                    p {
                        +"Paste comma or line seperated hashes or keys into the box below to add them to the playlist"
                    }
                    textarea(classes = "form-control") {
                        ref = hashRef
                        attrs.rows = "10"
                    }

                    div("mt-3") {
                        label("form-label") {
                            attrs.reactFor = "bplist"
                            div("text-truncate") {
                                +"Or upload bplist"
                            }
                        }
                        input(InputType.file, classes = "form-control") {
                            attrs.onChangeFunction = {
                                bplistUploadRef.current?.files?.let { it[0] }?.let { file ->
                                    val reader = FileReader()

                                    reader.onload = {
                                        val data = reader.result as String

                                        val csv = data.split(",")
                                        val hashes = if (csv.all { it.length == 32 }) {
                                            data
                                        } else {
                                            try {
                                                val playlist = jsonIgnoreUnknown.decodeFromString<Playlist>(data)
                                                playlist.songs.joinToString(",") { song -> song.hash }
                                            } catch (e: SerializationException) {
                                                // Bad bplist :O
                                                console.log(e)

                                                ""
                                            }
                                        }

                                        hashRef.current?.value = hashes
                                        0
                                    }

                                    reader.readAsText(file)
                                }
                            }
                            key = "bplist"
                            attrs.id = "bplist"
                            ref = bplistUploadRef
                        }
                    }

                    div("btn-group w-100 mt-5") {
                        routeLink(id?.let { "/playlists/$it" } ?: "/", className = "btn btn-secondary") {
                            +"Cancel"
                        }
                        button(classes = "btn btn-success") {
                            attrs.onClickFunction = {
                                val hashes = (hashRef.current?.value ?: "").split(",").filter { it.isNotBlank() }
                                startAdd(hashes)
                                setState {
                                    progress = 0 to hashes.size
                                }
                            }
                            +"Add"
                        }
                    }
                }
            }
        }
    }
}
