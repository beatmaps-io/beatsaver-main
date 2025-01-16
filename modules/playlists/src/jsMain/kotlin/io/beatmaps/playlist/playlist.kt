package io.beatmaps.playlist

import external.Axios
import external.dndExotics
import external.draggable
import external.droppable
import external.generateConfig
import external.invoke
import external.routeLink
import io.beatmaps.Config
import io.beatmaps.History
import io.beatmaps.api.CurateMap
import io.beatmaps.api.IssueCreationRequest
import io.beatmaps.api.MapDetailWithOrder
import io.beatmaps.api.PlaylistConstants
import io.beatmaps.api.PlaylistFull
import io.beatmaps.api.PlaylistMapRequest
import io.beatmaps.api.PlaylistPage
import io.beatmaps.captcha.ICaptchaHandler
import io.beatmaps.common.api.EPlaylistType
import io.beatmaps.common.api.PlaylistReportData
import io.beatmaps.globalContext
import io.beatmaps.index.beatmapInfo
import io.beatmaps.issues.reportModal
import io.beatmaps.setPageTitle
import io.beatmaps.shared.ModalButton
import io.beatmaps.shared.ModalCallbacks
import io.beatmaps.shared.ModalData
import io.beatmaps.shared.loadingElem
import io.beatmaps.shared.modal
import io.beatmaps.shared.modalContext
import io.beatmaps.shared.profileLink
import io.beatmaps.upload.UploadRequestConfig
import io.beatmaps.user.ProfileTab
import io.beatmaps.util.fcmemo
import io.beatmaps.util.orCatch
import io.beatmaps.util.textToContent
import io.beatmaps.util.useAudio
import kotlinx.html.classes
import kotlinx.html.id
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import kotlinx.serialization.SerializationException
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.xhr.FormData
import react.Props
import react.Suspense
import react.dom.a
import react.dom.button
import react.dom.div
import react.dom.i
import react.dom.img
import react.dom.p
import react.dom.span
import react.dom.textarea
import react.router.useNavigate
import react.router.useParams
import react.useContext
import react.useEffect
import react.useEffectOnce
import react.useEffectWithCleanup
import react.useRef
import react.useState
import kotlin.js.Promise
import kotlin.math.ceil

val playlistPage = fcmemo<Props>("playlistPage") {
    val (playlist, setPlaylist) = useState<PlaylistFull?>(null)
    val (maps, setMaps) = useState(listOf<MapDetailWithOrder>())
    val tokenRef = useRef(Axios.CancelToken.source())

    val modalRef = useRef<ModalCallbacks>()
    val reasonRef = useRef<HTMLTextAreaElement>()
    val captchaRef = useRef<ICaptchaHandler>()
    val errorRef = useRef<List<String>>()
    val itemsPerPage = PlaylistConstants.PAGE_SIZE

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

    fun delete(): Promise<Boolean> {
        val data = FormData()
        data.append("deleted", "true")
        data.append("reason", reasonRef.current?.value ?: "")

        return Axios.post<String>(
            "${Config.apibase}/playlists/id/$id/edit", data,
            UploadRequestConfig { }
        ).then { r ->
            if (r.status == 200) {
                history.push(playlist?.owner?.profileLink(ProfileTab.PLAYLISTS) ?: "/")
            }
            true
        }.catch {
            false
        }
    }

    fun curate(playlistId: Int, curated: Boolean = true) {
        Axios.post<PlaylistFull>("${Config.apibase}/playlists/curate", CurateMap(playlistId, curated), generateConfig<CurateMap, PlaylistFull>()).then({
            setPlaylist(it.data)
        }) { }
    }

    fun report(playlistId: Int) =
        captchaRef.current?.let { cc ->
            cc.execute()?.then { captcha ->
                val reason = reasonRef.current?.value?.trim() ?: ""
                Axios.post<String>(
                    "${Config.apibase}/issues/create",
                    IssueCreationRequest(captcha, reason, PlaylistReportData(playlistId)),
                    generateConfig<IssueCreationRequest, String>(validStatus = arrayOf(201))
                ).then {
                    history.push("/issues/${it.data}")
                    true
                }
            }?.orCatch {
                errorRef.current = listOfNotNull(it.message)
                false
            }
        } ?: Promise.resolve(false)

    useEffectOnce {
        setPageTitle("Playlist")
    }

    useEffectWithCleanup(params) {
        tokenRef.current = Axios.CancelToken.source()
        setPlaylist(null)
        setMaps(listOf())
        onCleanup {
            tokenRef.current?.cancel("Another request started")
        }
    }

    useEffect(playlist) {
        if (playlist == null) loadPage()
    }

    modal {
        attrs.callbacks = modalRef
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
                            routeLink("${pl.link()}/edit", className = "btn btn-primary") {
                                +"Edit"
                            }
                            if (pl.type != EPlaylistType.Search) {
                                routeLink("${pl.link()}/add", className = "btn btn-purple") {
                                    +"Multi-Add"
                                }
                            }
                            a("#", classes = "btn btn-danger") {
                                attrs.onClickFunction = {
                                    it.preventDefault()
                                    modalRef.current?.showDialog?.invoke(
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
                        routeLink(pl.owner.profileLink(ProfileTab.PLAYLISTS), className = "list-group-item d-flex justify-content-between") {
                            +"Created by"
                            span("text-truncate ms-4") {
                                attrs.title = pl.owner.name
                                +pl.owner.name
                            }
                        }
                        pl.curator?.let { curator ->
                            routeLink(curator.profileLink(ProfileTab.CURATED), className = "list-group-item d-flex justify-content-between") {
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
                        a(pl.oneClickURL(), classes = "btn btn-info") {
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
                    if (userData?.suspended == false && !userData.admin && userData.userId != pl.owner.id) {
                        div("btn-group") {
                            button(classes = "btn btn-danger") {
                                val text = "Report"
                                attrs.id = "report"
                                attrs.title = text
                                attrs.attributes["aria-label"] = text
                                attrs.onClickFunction = {
                                    it.preventDefault()
                                    modalRef.current?.showDialog?.invoke(
                                        ModalData(
                                            "Report playlist",
                                            bodyCallback = {
                                                reportModal {
                                                    attrs.subject = "playlist"
                                                    attrs.reasonRef = reasonRef
                                                    attrs.captchaRef = captchaRef
                                                    attrs.errorsRef = errorRef
                                                }
                                            },
                                            buttons = listOf(
                                                ModalButton("Report", "danger") { report(pl.playlistId) },
                                                ModalButton("Cancel")
                                            )
                                        )
                                    )
                                }
                                i("fas fa-flag me-2") { }
                                +text
                            }
                        }
                    }
                }
            }
            div("col-lg-8") {
                Suspense {
                    attrs.fallback = loadingElem
                    if (playlist?.owner?.id == userData?.userId && playlist?.type?.orderable == true) {
                        dndExotics.dragDropContext {
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

                                        playlistMapEditable {
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
                                    attrs.obj = it.map
                                    attrs.version = it.map.publishedVersion()
                                    attrs.audio = audio
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
