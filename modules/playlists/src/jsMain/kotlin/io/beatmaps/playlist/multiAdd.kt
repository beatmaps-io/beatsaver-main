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
import kotlinx.serialization.SerializationException
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
import web.events.ProgressEvent
import web.events.addEventListener
import web.file.FileReader
import web.html.HTMLInputElement
import web.html.HTMLTextAreaElement
import web.html.InputType
import web.timers.setTimeout

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
            setTimeout(
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
        className = ClassName("card border-dark w-50 m-auto")
        div {
            className = ClassName("card-header")
            +"Add maps to playlist"
        }
        div {
            className = ClassName("card-body")
            progress?.let { progress ->
                if (progress.first >= 0) {
                    p {
                        className = ClassName("h4 text-center mt-4")
                        +"Adding maps to playlist (${progress.first} / ${progress.second})"
                    }
                    div {
                        className = ClassName("progress m-4")
                        div {
                            className = ClassName("progress-bar progress-bar-striped progress-bar-animated bg-info")
                            role = AriaRole.progressbar
                            style = jso {
                                width = ((progress.first * 100f) / progress.second).toInt().pct
                            }
                        }
                    }
                } else {
                    p {
                        className = ClassName("h4 text-center")
                        +"Error adding maps to playlist"
                        br {}
                        +"Hashes/keys are invalid or the maps don't exist"
                    }
                    div {
                        className = ClassName("btn-group w-100 mt-5")
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
                    className = ClassName("form-control")
                    ref = hashRef
                    rows = 10
                }

                div {
                    className = ClassName("mt-3")
                    label {
                        className = ClassName("form-label")
                        htmlFor = "bplist"
                        div {
                            className = ClassName("text-truncate")
                            +"Or upload bplist"
                        }
                    }
                    input {
                        type = InputType.file
                        className = ClassName("form-control")
                        onChange = {
                            bplistUploadRef.current?.files?.let { it[0] }?.let { file ->
                                val reader = FileReader()

                                reader.addEventListener(
                                    ProgressEvent.LOAD,
                                    {
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
                                    }
                                )

                                reader.readAsText(file)
                            }
                        }
                        key = "bplist"
                        this.id = "bplist"
                        ref = bplistUploadRef
                    }
                }

                div {
                    className = ClassName("btn-group w-100 mt-5")
                    routeLink(id?.let { "/playlists/$it" } ?: "/", className = "btn btn-secondary") {
                        +"Cancel"
                    }
                    button {
                        className = ClassName("btn btn-success")
                        onClick = {
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
