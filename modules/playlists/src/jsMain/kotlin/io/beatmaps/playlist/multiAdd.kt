package io.beatmaps.playlist

import external.Axios
import external.generateConfig
import external.routeLink
import io.beatmaps.Config
import io.beatmaps.History
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.Playlist
import io.beatmaps.api.PlaylistBatchRequest
import io.beatmaps.common.jsonIgnoreUnknown
import io.beatmaps.setPageTitle
import io.beatmaps.util.fcmemo
import io.beatmaps.util.hashRegex
import js.objects.jso
import kotlinx.browser.window
import kotlinx.serialization.SerializationException
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.files.FileReader
import org.w3c.files.get
import react.Props
import react.dom.aria.AriaRole
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.textarea
import react.router.useNavigate
import react.router.useParams
import react.useEffectOnce
import react.useRef
import react.useState
import web.cssom.ClassName
import web.cssom.pct
import web.html.InputType

val multiAddPlaylist = fcmemo<Props>("multiAddPlaylist") {
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

    div {
        attrs.className = ClassName("card border-dark w-50 m-auto")
        div {
            attrs.className = ClassName("card-header")
            +"Add maps to playlist"
        }
        div {
            attrs.className = ClassName("card-body")
            progress?.let { progress ->
                if (progress.first >= 0) {
                    p {
                        attrs.className = ClassName("h4 text-center mt-4")
                        +"Adding maps to playlist (${progress.first} / ${progress.second})"
                    }
                    div {
                        attrs.className = ClassName("progress m-4")
                        div {
                            attrs.className = ClassName("progress-bar progress-bar-striped progress-bar-animated bg-info")
                            attrs.role = AriaRole.progressbar
                            attrs.style = jso {
                                width = ((progress.first * 100f) / progress.second).toInt().pct
                            }
                        }
                    }
                } else {
                    p {
                        attrs.className = ClassName("h4 text-center")
                        +"Error adding maps to playlist"
                        br {}
                        +"Hashes/keys are invalid or the maps don't exist"
                    }
                    div {
                        attrs.className = ClassName("btn-group w-100 mt-5")
                        routeLink(id?.let { "/playlists/$it" } ?: "/", className = "btn btn-secondary") {
                            +"Back"
                        }
                    }
                }
            } ?: run {
                p {
                    +"Paste comma or line seperated hashes or keys into the box below to add them to the playlist"
                }
                textarea {
                    attrs.className = ClassName("form-control")
                    ref = hashRef
                    attrs.rows = 10
                }

                div {
                    attrs.className = ClassName("mt-3")
                    label {
                        attrs.className = ClassName("form-label")
                        attrs.htmlFor = "bplist"
                        div {
                            attrs.className = ClassName("text-truncate")
                            +"Or upload bplist"
                        }
                    }
                    input {
                        attrs.type = InputType.file
                        attrs.className = ClassName("form-control")
                        attrs.onChange = {
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

                div {
                    attrs.className = ClassName("btn-group w-100 mt-5")
                    routeLink(id?.let { "/playlists/$it" } ?: "/", className = "btn btn-secondary") {
                        +"Cancel"
                    }
                    button {
                        attrs.className = ClassName("btn btn-success")
                        attrs.onClick = {
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
