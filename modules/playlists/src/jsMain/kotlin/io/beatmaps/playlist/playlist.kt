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
import io.beatmaps.api.UploadResponse
import io.beatmaps.captcha.ICaptchaHandler
import io.beatmaps.common.api.EIssueType
import io.beatmaps.common.api.EPlaylistType
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
import kotlinx.serialization.SerializationException
import react.Props
import react.Suspense
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.textarea
import react.router.useNavigate
import react.router.useParams
import react.use
import react.useEffect
import react.useEffectOnce
import react.useEffectWithCleanup
import react.useRef
import react.useState
import web.cssom.ClassName
import web.form.FormData
import web.html.HTMLTextAreaElement
import kotlin.js.Promise

val playlistPage = fcmemo<Props>("playlistPage") { props ->
    val (playlist, setPlaylist) = useState<PlaylistFull?>(null)
    val (maps, setMaps) = useState(listOf<MapDetailWithOrder>())
    val tokenRef = useRef(Axios.CancelToken.source())

    val modalRef = useRef<ModalCallbacks>()
    val reasonRef = useRef<HTMLTextAreaElement>()
    val captchaRef = useRef<ICaptchaHandler>()
    val errorRef = useRef<List<String>>()
    val itemsPerPage = PlaylistConstants.PAGE_SIZE

    val audio = useAudio()
    val userData = use(globalContext)

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

    fun reorderMaps(start: Int, end: Int) =
        reorderMaps(maps, start, end)?.let {
            val elem = it[end]
            updateOrder(elem.map.id, elem.order)
            setMaps(it)
        }

    fun delete(): Promise<Boolean> {
        val data = FormData()
        data.append("deleted", "true")
        data.append("reason", reasonRef.current?.value ?: "")

        return Axios.post<UploadResponse>(
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
                    IssueCreationRequest(captcha, reason, playlistId, EIssueType.PlaylistReport),
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
        callbacks = modalRef
    }

    modalContext.Provider {
        value = modalRef

        div {
            className = ClassName("row mt-3")
            div {
                className = ClassName("playlist-info col-lg-4")
                playlist?.let { pl ->
                    if (pl.deletedAt != null) {
                        div {
                            className = ClassName("alert alert-danger text-center")
                            +"DELETED"
                        }
                    } else if (pl.type != EPlaylistType.System && (pl.owner.id == userData?.userId || userData?.admin == true)) {
                        div {
                            className = ClassName("btn-group")
                            routeLink("${pl.link()}/edit", className = "btn btn-primary") {
                                +"Edit"
                            }
                            if (pl.type != EPlaylistType.Search) {
                                routeLink("${pl.link()}/add", className = "btn btn-purple") {
                                    +"Multi-Add"
                                }
                            }
                            a {
                                href = "#"
                                className = ClassName("btn btn-danger")
                                onClick = {
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
                                                    textarea {
                                                        className = ClassName("form-control")
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
                        div {
                            className = ClassName("break")
                        }
                        div {
                            className = ClassName("btn-group")
                            a {
                                href = "#"
                                className = ClassName("btn " + if (pl.curatedAt == null) "btn-green" else "btn-expert")
                                val text = ((if (pl.curatedAt == null) "" else "Un-") + "Curate")
                                title = text
                                ariaLabel = text
                                onClick = {
                                    it.preventDefault()
                                    curate(pl.playlistId, pl.curatedAt == null)
                                }
                                +text
                            }
                        }
                    }
                    div {
                        className = ClassName("list-group")
                        img {
                            alt = "Cover"
                            src = pl.playlistImage512 ?: pl.playlistImage
                        }
                        div {
                            className = ClassName("list-group-item d-flex justify-content-between")
                            +"Name"
                            span {
                                className = ClassName("text-truncate ms-4")
                                +pl.name
                            }
                        }
                        routeLink(pl.owner.profileLink(ProfileTab.PLAYLISTS), className = "list-group-item d-flex justify-content-between") {
                            +"Created by"
                            span {
                                className = ClassName("text-truncate ms-4")
                                title = pl.owner.name
                                +pl.owner.name
                            }
                        }
                        pl.curator?.let { curator ->
                            routeLink(curator.profileLink(ProfileTab.CURATED), className = "list-group-item d-flex justify-content-between") {
                                +"Curated by"
                                span {
                                    className = ClassName("text-truncate ms-4")
                                    +curator.name
                                }
                            }
                        }
                        div {
                            className = ClassName("list-group-item d-flex justify-content-between")
                            +"Maps"
                            span {
                                className = ClassName("text-truncate ms-4")
                                +maps.size.toString()
                            }
                        }
                        if (pl.description.isNotBlank()) {
                            div {
                                className = ClassName("list-group-item ws-normal text-break")
                                textToContent(pl.description)
                            }
                        }
                    }
                    div {
                        className = ClassName("btn-group d-flex")
                        a {
                            href = pl.downloadURL
                            className = ClassName("btn btn-success")
                            +"Download"
                        }
                        a {
                            href = pl.oneClickURL()
                            className = ClassName("btn btn-info")
                            +"One-Click"
                        }
                    }
                    if (maps.isNotEmpty()) {
                        div {
                            className = ClassName("list-group")
                            div {
                                className = ClassName("list-group-item ws-normal")
                                div {
                                    className = ClassName("mb-1")
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
                        div {
                            className = ClassName("btn-group")
                            button {
                                className = ClassName("btn btn-danger")
                                val text = "Report"
                                this.id = "report"
                                title = text
                                ariaLabel = text
                                onClick = {
                                    it.preventDefault()
                                    modalRef.current?.showDialog?.invoke(
                                        ModalData(
                                            "Report playlist",
                                            bodyCallback = {
                                                reportModal {
                                                    subject = "playlist"
                                                    this.reasonRef = reasonRef
                                                    this.captchaRef = captchaRef
                                                    errorsRef = errorRef
                                                }
                                            },
                                            buttons = listOf(
                                                ModalButton("Report", "danger") { report(pl.playlistId) },
                                                ModalButton("Cancel")
                                            )
                                        )
                                    )
                                }
                                i {
                                    className = ClassName("fas fa-flag me-2")
                                }
                                +text
                            }
                        }
                    }
                }
            }
            div {
                className = ClassName("col-lg-8")
                Suspense {
                    fallback = loadingElem
                    if (playlist?.owner?.id == userData?.userId && playlist?.type?.orderable == true) {
                        dndExotics.dragDropContext {
                            onDragEnd = {
                                it.destination?.let { dest ->
                                    reorderMaps(it.source.index, dest.index)
                                }
                            }
                            droppable("playlist") {
                                className = ClassName("playlist")
                                maps.mapIndexed { idx, it ->
                                    draggable(it.map.id, idx) {
                                        className = ClassName("drag-beatmap")

                                        playlistMapEditable {
                                            obj = it.map
                                            this.audio = audio
                                            playlistKey = playlist.playlistId
                                            removeMap = {
                                                setMaps(maps - it)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        div {
                            className = ClassName("playlist")
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
}
