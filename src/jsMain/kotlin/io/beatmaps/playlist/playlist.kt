package io.beatmaps.playlist

import external.Axios
import external.DragAndDrop.DragDropContext
import external.draggable
import external.droppable
import external.generateConfig
import external.invoke
import external.routeLink
import io.beatmaps.Config
import io.beatmaps.History
import io.beatmaps.api.CurateMap
import io.beatmaps.api.MapDetailWithOrder
import io.beatmaps.api.PlaylistFull
import io.beatmaps.api.PlaylistMapRequest
import io.beatmaps.api.PlaylistPage
import io.beatmaps.common.api.EPlaylistType
import io.beatmaps.globalContext
import io.beatmaps.index.ModalButton
import io.beatmaps.index.ModalComponent
import io.beatmaps.index.ModalData
import io.beatmaps.index.beatmapInfo
import io.beatmaps.index.modal
import io.beatmaps.index.modalContext
import io.beatmaps.setPageTitle
import io.beatmaps.upload.UploadRequestConfig
import io.beatmaps.util.textToContent
import io.beatmaps.util.useAudio
import kotlinx.html.classes
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import kotlinx.serialization.SerializationException
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.xhr.FormData
import react.Props
import react.dom.a
import react.dom.div
import react.dom.img
import react.dom.p
import react.dom.span
import react.dom.textarea
import react.fc
import react.ref
import react.router.useNavigate
import react.router.useParams
import react.useContext
import react.useEffect
import react.useEffectOnce
import react.useRef
import react.useState
import kotlin.math.ceil

val playlistPage = fc<Props> {
    val (playlist, setPlaylist) = useState<PlaylistFull?>(null)
    val (maps, setMaps) = useState(listOf<MapDetailWithOrder>())
    val tokenRef = useRef(Axios.CancelToken.source())

    val modalRef = useRef<ModalComponent>()
    val reasonRef = useRef<HTMLTextAreaElement>()
    val itemsPerPage = 100

    val audio = useAudio()
    val userData = useContext(globalContext)

    val history = History(useNavigate())
    val params = useParams()
    val id = params["id"]

    fun loadPage(mapsLocal: List<MapDetailWithOrder> = listOf(), page: Int? = 0) {
        Axios.get<PlaylistPage>(
            "${Config.apibase}/playlists/id/$id/$page",
            generateConfig<String, PlaylistPage>(tokenRef.current?.token)
        ).then {
            setPageTitle("Playlist - ${it.data.playlist?.name}")
            setPlaylist(it.data.playlist)
            val newMaps = mapsLocal.plus(it.data.maps ?: listOf())
            setMaps(newMaps.sortedBy { m -> m.order })

            if ((it.data.maps?.size ?: 0) >= itemsPerPage) {
                loadPage(newMaps, (page ?: 0) + 1)
            }
        }.catch {
            when (it) {
                is SerializationException -> history.push("/")
                else -> {} // Ignore
            }
        }
    }

    fun updateOrder(mapId: String, order: Float) {
        Axios.post<String>(
            "${Config.apibase}/playlists/id/$id/add",
            PlaylistMapRequest(mapId, true, order),
            generateConfig<PlaylistMapRequest, String>()
        )
    }

    fun reorderMaps(start: Int, end: Int) {
        if (start == end) {
            return
        }

        setMaps(
            maps.toMutableList().also { mutable ->
                val elem = mutable.removeAt(start)

                val previousOrder = if (end <= 0) 0f else mutable[end - 1].order
                val nextOrder = if (end > 0 && end >= mutable.size - 1) mutable[end - 1].order + 2 else mutable[end].order

                val midOrder = (previousOrder + nextOrder) / 2
                val nextWholeOrder = ceil(previousOrder)
                val previousIsWhole = nextWholeOrder == previousOrder

                val newOrder = if (nextOrder - previousOrder > 1) {
                    if (previousIsWhole) {
                        previousOrder + 1 // 1, 4 -> 2
                    } else {
                        nextWholeOrder // 1.1, 4 -> 2
                    }
                } else if (nextWholeOrder != ceil(nextOrder) && !previousIsWhole) {
                    nextWholeOrder // 1.6, 2.1 -> 2
                } else {
                    midOrder // 1, 2 -> 1.5
                }

                mutable.add(end, elem.copy(order = newOrder))
                updateOrder(elem.map.id, newOrder)
            }
        )
    }

    fun delete() {
        val data = FormData()
        data.append("deleted", "true")
        data.append("reason", reasonRef.current?.value ?: "")

        Axios.post<String>(
            "${Config.apibase}/playlists/id/$id/edit", data,
            UploadRequestConfig { }
        ).then { r ->
            if (r.status == 200) {
                history.push(playlist?.owner?.profileLink("playlists") ?: "/")
            }
        }.catch {
            // Do nothing
        }
    }

    fun curate(playlistId: Int, curated: Boolean = true) {
        Axios.post<PlaylistFull>("${Config.apibase}/playlists/curate", CurateMap(playlistId, curated), generateConfig<CurateMap, PlaylistFull>()).then({
            setPlaylist(it.data)
        }) { }
    }

    useEffectOnce {
        setPageTitle("Playlist")
    }

    useEffect(params) {
        tokenRef.current = Axios.CancelToken.source()
        setPlaylist(null)
        setMaps(listOf())
        cleanup {
            tokenRef.current?.cancel("Another request started")
        }
    }

    useEffect(playlist) {
        if (playlist == null) loadPage()
    }

    modal {
        ref = modalRef
    }

    modalContext.Provider {
        attrs.value = modalRef

        div("row mt-3") {
            div("playlist-info col-lg-4") {
                playlist?.let { pl ->
                    if (pl.deletedAt != null) {
                        div("alert alert-danger text-center") {
                            +"DELETED"
                        }
                    } else if (pl.type != EPlaylistType.System && (pl.owner.id == userData?.userId || userData?.admin == true)) {
                        div("btn-group") {
                            routeLink("/playlists/${pl.playlistId}/edit", className = "btn btn-primary") {
                                +"Edit"
                            }
                            if (pl.type != EPlaylistType.Search) {
                                routeLink("/playlists/${pl.playlistId}/add", className = "btn btn-purple") {
                                    +"Multi-Add"
                                }
                            }
                            a("#", classes = "btn btn-danger") {
                                attrs.onClickFunction = {
                                    it.preventDefault()
                                    modalRef.current?.showDialog(
                                        ModalData(
                                            "Delete playlist",
                                            bodyCallback = {
                                                p {
                                                    +"Are you sure? This action cannot be reversed."
                                                }
                                                if (userData.admin) {
                                                    p {
                                                        +"Reason for action:"
                                                    }
                                                    textarea(classes = "form-control") {
                                                        ref = reasonRef
                                                    }
                                                }
                                            },
                                            buttons = listOf(ModalButton("YES, DELETE", "danger", ::delete), ModalButton("Cancel"))
                                        )
                                    )
                                }
                                +"Delete"
                            }
                        }
                    }
                    if (pl.type != EPlaylistType.System && pl.deletedAt == null && userData?.curator == true) {
                        div("break") {}
                        div("btn-group") {
                            a("#", classes = "btn " + if (pl.curatedAt == null) "btn-green" else "btn-expert") {
                                val text = ((if (pl.curatedAt == null) "" else "Un-") + "Curate")
                                attrs.title = text
                                attrs.attributes["aria-label"] = text
                                attrs.onClickFunction = {
                                    it.preventDefault()
                                    curate(pl.playlistId, pl.curatedAt == null)
                                }
                                +text
                            }
                        }
                    }
                    div("list-group") {
                        img("Cover", pl.playlistImage512 ?: pl.playlistImage) { }
                        div("list-group-item d-flex justify-content-between") {
                            +"Name"
                            span("text-truncate ms-4") {
                                +pl.name
                            }
                        }
                        routeLink(pl.owner.profileLink(), className = "list-group-item d-flex justify-content-between") {
                            +"Created by"
                            span("text-truncate ms-4") {
                                attrs.title = pl.owner.name
                                +pl.owner.name
                            }
                        }
                        pl.curator?.let { curator ->
                            div("list-group-item d-flex justify-content-between") {
                                +"Curated by"
                                span("text-truncate ms-4") {
                                    +curator.name
                                }
                            }
                        }
                        div("list-group-item d-flex justify-content-between") {
                            +"Maps"
                            span("text-truncate ms-4") {
                                +maps.size.toString()
                            }
                        }
                        if (pl.description.isNotBlank()) {
                            div("list-group-item ws-normal text-break") {
                                textToContent(pl.description)
                            }
                        }
                    }
                    div("btn-group d-flex") {
                        a(pl.downloadURL, classes = "btn btn-success") {
                            +"Download"
                        }
                        a("bsplaylist://playlist/${pl.downloadURL}/beatsaver-${pl.playlistId}.bplist", classes = "btn btn-info") {
                            +"One-Click"
                        }
                    }
                    if (maps.isNotEmpty()) {
                        div("list-group") {
                            div("list-group-item ws-normal") {
                                div("mb-1") {
                                    +"Mappers"
                                }
                                maps
                                    .flatMap { (it.map.collaborators ?: listOf()) + it.map.uploader }
                                    .groupBy { it.id }.entries.map {
                                        it.value.size to it.value.first()
                                    }.sortedByDescending { it.first }.mapIndexed { idx, it ->
                                        if (idx > 0) {
                                            +", "
                                        }
                                        routeLink(it.second.profileLink()) {
                                            +it.second.name
                                        }
                                    }
                            }
                        }
                    }
                }
            }
            div("col-lg-8") {
                if (playlist?.owner?.id == userData?.userId && playlist?.type?.orderable == true) {
                    DragDropContext {
                        attrs.onDragEnd = {
                            it.destination?.let { dest ->
                                reorderMaps(it.source.index, dest.index)
                            }
                        }
                        droppable("playlist") {
                            attrs.classes = setOf("playlist")
                            maps.mapIndexed { idx, it ->
                                draggable(it.map.id, idx) {
                                    attrs.classes = setOf("drag-beatmap")

                                    removeMapPlaylist {
                                        attrs.obj = it.map
                                        attrs.audio = audio
                                        attrs.playlistKey = playlist.playlistId
                                        attrs.removeMap = {
                                            setMaps(maps - it)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    div("playlist") {
                        maps.map {
                            beatmapInfo {
                                obj = it.map
                                version = it.map.publishedVersion()
                                this.audio = audio
                            }
                        }
                    }
                }
            }
        }
    }
}
