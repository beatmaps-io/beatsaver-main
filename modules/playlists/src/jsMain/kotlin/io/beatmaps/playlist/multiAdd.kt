package io.beatmaps.playlist

import external.Axios
import external.generateConfig
import external.reactFor
import external.routeLink
import io.beatmaps.Config
import io.beatmaps.History
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.Playlist
import io.beatmaps.api.PlaylistBatchRequest
import io.beatmaps.common.jsonIgnoreUnknown
import io.beatmaps.setPageTitle
import io.beatmaps.util.hashRegex
import kotlinx.browser.window
import kotlinx.html.InputType
import kotlinx.html.id
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import kotlinx.html.role
import kotlinx.serialization.SerializationException
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.files.FileReader
import org.w3c.files.get
import react.Props
import react.dom.br
import react.dom.button
import react.dom.div
import react.dom.input
import react.dom.jsStyle
import react.dom.label
import react.dom.p
import react.dom.textarea
import react.fc
import react.router.useNavigate
import react.router.useParams
import react.useEffectOnce
import react.useRef
import react.useState

val multiAddPlaylist = fc<Props>("multiAddPlaylist") {
    val (progress, setProgress) = useState<Pair<Int, Int>?>(null)

    val hashRef = useRef<HTMLTextAreaElement>()
    val bplistUploadRef = useRef<HTMLInputElement>()

    val history = History(useNavigate())
    val params = useParams()

    useEffectOnce {
        setPageTitle("Playlist - Multi-Add")
    }

    fun doAdd(queue: List<List<String>>) {
        if (queue.isEmpty()) {
            if (progress?.first == progress?.second) {
                history.push("/playlists/${params["id"]}")
            }

            return
        }

        val hashes = queue.first()
        val (keyList, hashList) = hashes.partition { !hashRegex.matches(it) }

        Axios.post<ActionResponse>(
            "${Config.apibase}/playlists/id/${params["id"]}/batch",
            PlaylistBatchRequest(hashList, keyList, inPlaylist = true, ignoreUnknown = true),
            generateConfig<PlaylistBatchRequest, ActionResponse>()
        ).then {
            setProgress(progress?.let { p -> p.first + hashes.size to p.second })
            window.setTimeout(
                {
                    doAdd(queue.minus(setOf(hashes)))
                },
                500
            )
        }.catch {
            setProgress(progress?.let { p -> -1 to p.second })
        }
    }

    fun startAdd(hashes: List<String>) {
        doAdd(hashes.chunked(100))
    }

    val id = params["id"]

    div("card border-dark w-50 m-auto") {
        div("card-header") {
            +"Add maps to playlist"
        }
        div(classes = "card-body") {
            progress?.let { progress ->
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
                            val hashes = (hashRef.current?.value ?: "").split(",", "\r\n", "\n").filter { it.isNotBlank() }
                            startAdd(hashes)
                            setProgress(0 to hashes.size)
                        }
                        +"Add"
                    }
                }
            }
        }
    }
}
