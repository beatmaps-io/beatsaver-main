package io.beatmaps.maps

import external.Axios
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.History
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.AiDeclaration
import io.beatmaps.api.BookmarkRequest
import io.beatmaps.api.CurateMap
import io.beatmaps.api.IssueCreationRequest
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapInfoUpdate
import io.beatmaps.api.MarkNsfw
import io.beatmaps.api.SimpleMapInfoUpdate
import io.beatmaps.api.StateUpdate
import io.beatmaps.captcha.ICaptchaHandler
import io.beatmaps.common.api.AiDeclarationType
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.api.MapAttr
import io.beatmaps.common.api.MapReportData
import io.beatmaps.globalContext
import io.beatmaps.issues.reportModal
import io.beatmaps.maps.collab.collaboratorLeave
import io.beatmaps.maps.collab.collaboratorPicker
import io.beatmaps.playlist.playlists
import io.beatmaps.shared.AudioPreviewSize
import io.beatmaps.shared.ModalButton
import io.beatmaps.shared.ModalData
import io.beatmaps.shared.audioPreview
import io.beatmaps.shared.coloredCard
import io.beatmaps.shared.form.errors
import io.beatmaps.shared.map.bookmarkButton
import io.beatmaps.shared.map.links
import io.beatmaps.shared.modalContext
import io.beatmaps.util.orCatch
import io.beatmaps.util.textToContent
import io.beatmaps.util.useAudio
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.html.InputType
import kotlinx.html.id
import kotlinx.html.js.onClickFunction
import kotlinx.html.js.onMouseUpFunction
import kotlinx.html.title
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.Event
import react.Props
import react.Suspense
import react.dom.a
import react.dom.button
import react.dom.div
import react.dom.i
import react.dom.input
import react.dom.p
import react.dom.span
import react.dom.textarea
import react.fc
import react.router.useNavigate
import react.useContext
import react.useEffect
import react.useRef
import react.useState
import kotlin.collections.set
import kotlin.js.Promise

external interface MapInfoProps : Props {
    var mapInfo: MapDetail
    var reloadMap: () -> Unit
    var deleteMap: (Boolean) -> Unit
    var updateMapinfo: (MapDetail) -> Unit
}

val mapInfo = fc<MapInfoProps>("mapInfo") { props ->
    val inputRef = useRef<HTMLInputElement>()
    val textareaRef = useRef<HTMLTextAreaElement>()
    val reasonRef = useRef<HTMLTextAreaElement>()
    val audio = useAudio()
    val history = History(useNavigate())

    val (tags, setTags) = useState(props.mapInfo.tags.toSet())
    val (loading, setLoading) = useState(false)
    val (errors, setErrors) = useState(emptyList<String>())
    val errorRef = useRef<List<String>>()
    val (editing, setEditing) = useState(false)
    val (dropdown, setDropdown) = useState(false)

    useEffect {
        val hideDropdown = { _: Event ->
            setDropdown(false)
        }
        document.addEventListener("mouseup", hideDropdown)
        cleanup {
            document.removeEventListener("mouseup", hideDropdown)
        }
    }

    val userData = useContext(globalContext)
    val loggedInId = userData?.userId
    val isOwnerLocal = loggedInId == props.mapInfo.uploader.id
    val isCollaboratorLocal = props.mapInfo.collaborators?.let { loggedInId in it.map { collaborator -> collaborator.id } } == true

    val modal = useContext(modalContext)
    val captchaRef = useRef<ICaptchaHandler>()

    fun recall() =
        props.mapInfo.publishedVersion()?.hash?.let { hash ->
            setLoading(true)

            Axios.post<ActionResponse>("${Config.apibase}/testplay/state", StateUpdate(hash, EMapState.Uploaded, props.mapInfo.intId(), reasonRef.current?.value), generateConfig<StateUpdate, ActionResponse>(validStatus = arrayOf(200, 400)))
                .then({
                    it.data.success.also { success ->
                        if (success) {
                            props.reloadMap()
                        } else {
                            setErrors(it.data.errors)
                        }
                    }
                }) {
                    setLoading(false)
                    false
                }
        } ?: Promise.resolve(true)

    fun delete(): Promise<Boolean> {
        setLoading(true)

        return Axios.post<String>("${Config.apibase}/maps/update", MapInfoUpdate(props.mapInfo.intId(), deleted = true, reason = reasonRef.current?.value?.trim()), generateConfig<MapInfoUpdate, String>()).then({
            props.deleteMap(isOwnerLocal)
            true
        }) {
            setLoading(false)
            false
        }
    }

    fun curate(curated: Boolean = true): Promise<Boolean> {
        setLoading(true)

        return Axios.post<String>("${Config.apibase}/maps/curate", CurateMap(props.mapInfo.intId(), curated, reason = reasonRef.current?.value?.trim()), generateConfig<CurateMap, String>()).then({
            props.reloadMap()
            true
        }) {
            setLoading(false)
            false
        }
    }

    fun declareAi(automapper: Boolean = true) {
        setLoading(true)

        Axios.post<String>("${Config.apibase}/maps/declareai", AiDeclaration(props.mapInfo.intId(), automapper), generateConfig<AiDeclaration, String>()).then({
            props.reloadMap()
        }) {
            setLoading(false)
        }
    }

    fun markNsfw(nsfw: Boolean = true) {
        setLoading(true)

        Axios.post<String>("${Config.apibase}/maps/marknsfw", MarkNsfw(props.mapInfo.intId(), nsfw), generateConfig<MarkNsfw, String>()).then({
            props.reloadMap()
        }) {
            setLoading(false)
        }
    }

    fun bookmark(bookmarked: Boolean) {
        setLoading(true)

        Axios.post<String>("${Config.apibase}/bookmark", BookmarkRequest(props.mapInfo.id, bookmarked = bookmarked), generateConfig<BookmarkRequest, String>()).then({
            props.reloadMap()
        }) {
            setLoading(false)
        }
    }

    fun report() =
        captchaRef.current?.let { cc ->
            cc.execute()?.then { captcha ->
                val reason = reasonRef.current?.value?.trim() ?: ""
                Axios.post<String>(
                    "${Config.apibase}/issues/create",
                    IssueCreationRequest(captcha, reason, MapReportData(props.mapInfo.id)),
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

    val deleted = props.mapInfo.deletedAt != null

    if (props.mapInfo.let { it.declaredAi == AiDeclarationType.SageScore && isOwnerLocal }) {
        div("alert alert-danger alert-dismissible") {
            +"This map was automatically flagged as an AI-generated map. If you believe this was a mistake, please report it in the "
            a("https://discord.gg/rjVDapkMmj", classes = "alert-link") {
                +"BeatSaver Discord server"
            }
            +"."
            button(classes = "btn-close") {
                attrs.onClickFunction = {
                    it.preventDefault()
                    declareAi(true)
                }
            }
        }
    }

    val mapAttrs = listOfNotNull(
        if (props.mapInfo.ranked || props.mapInfo.blRanked) MapAttr.Ranked else null,
        if (props.mapInfo.curator != null) MapAttr.Curated else null
    ).ifEmpty {
        listOfNotNull(
            if (props.mapInfo.uploader.verifiedMapper) MapAttr.Verified else null
        )
    }

    coloredCard {
        attrs.color = mapAttrs.joinToString(" ") { it.color }
        attrs.title = mapAttrs.joinToString(" + ") { it.name }
        attrs.classes = "m-0"

        div("card-header d-flex" + if (deleted) " bg-danger" else "") {
            if (editing) {
                +"Edit map"
            } else {
                +props.mapInfo.name
            }
            div("link-buttons") {
                if (!deleted) {
                    props.mapInfo.mainVersion()?.let { version ->
                        div("thin-dd" + if (dropdown) " show" else "") {
                            a("#", classes = "dd") {
                                attrs.attributes["aria-label"] = "${if (dropdown) "Hide" else "Show"} dropdown"
                                attrs.onClickFunction = {
                                    setDropdown(!dropdown)
                                }
                                attrs.onMouseUpFunction = {
                                    it.stopPropagation()
                                }
                                i("fas fa-ellipsis-v") { }
                            }
                            div {
                                attrs.onMouseUpFunction = {
                                    if (it.target == it.currentTarget || it.target.let { d -> d is HTMLDivElement && d.className == "dropdown-divider" }) {
                                        it.stopPropagation()
                                    }
                                }
                                if (userData != null) {
                                    Suspense {
                                        playlists.addTo {
                                            attrs.map = props.mapInfo
                                        }
                                    }
                                    bookmarkButton {
                                        attrs.bookmarked = props.mapInfo.bookmarked == true
                                        attrs.onClick = { e, bm ->
                                            e.preventDefault()
                                            if (!loading) bookmark(!bm)
                                        }
                                    }

                                    div("dropdown-divider") {}
                                }

                                links {
                                    attrs.map = props.mapInfo
                                    attrs.version = version
                                }

                                if (userData != null) {
                                    div("dropdown-divider") {}
                                }

                                if (userData?.curator == true || isOwnerLocal) {
                                    a("#") {
                                        attrs.title = "Edit"
                                        attrs.attributes["aria-label"] = "Edit"
                                        attrs.onClickFunction = {
                                            it.preventDefault()
                                            setEditing(!editing)
                                            window.setTimeout(
                                                {
                                                    inputRef.current?.value = props.mapInfo.name
                                                },
                                                1
                                            )
                                        }
                                        span("dd-text") { +"Edit" }
                                        i("fas fa-pen text-warning") { }
                                    }
                                }

                                if (loggedInId != null && isCollaboratorLocal) {
                                    collaboratorLeave {
                                        attrs.map = props.mapInfo
                                        attrs.collaboratorId = loggedInId
                                        attrs.reloadMap = props.reloadMap
                                        attrs.modal = modal
                                    }
                                }

                                if (userData?.curator == true && !isOwnerLocal) {
                                    a("#") {
                                        val isCurated = props.mapInfo.curator != null
                                        val text = if (isCurated) "Uncurate" else "Curate"
                                        attrs.title = text
                                        attrs.attributes["aria-label"] = text
                                        attrs.onClickFunction = {
                                            it.preventDefault()
                                            if (!isCurated) {
                                                modal?.current?.showDialog?.invoke(
                                                    ModalData(
                                                        "Curate map",
                                                        bodyCallback = {
                                                            p {
                                                                +"Are you sure you want to curate this map?"
                                                            }
                                                        },
                                                        buttons = listOf(
                                                            ModalButton("Curate", "primary") { curate() },
                                                            ModalButton("Cancel")
                                                        )
                                                    )
                                                )
                                            } else {
                                                modal?.current?.showDialog?.invoke(
                                                    ModalData(
                                                        "Uncurate map",
                                                        bodyCallback = {
                                                            p {
                                                                +"Are you sure you want to uncurate this map? If so, please provide a comprehensive reason."
                                                            }
                                                            p {
                                                                +"Reason for action:"
                                                            }
                                                            textarea(classes = "form-control") {
                                                                ref = reasonRef
                                                            }
                                                        },
                                                        buttons = listOf(
                                                            ModalButton("Uncurate", "primary") { curate(false) },
                                                            ModalButton("Cancel")
                                                        )
                                                    )
                                                )
                                            }
                                        }
                                        span("dd-text") { +text }
                                        i("fas fa-award " + if (isCurated) "text-danger-light" else "text-success") { }
                                    }
                                }
                                if (userData?.admin == true) {
                                    a("#") {
                                        val tooltip = if (props.mapInfo.declaredAi.markAsBot) "Flag as Human-made Map" else "Flag as AI-assisted Map"
                                        attrs.title = tooltip
                                        attrs.attributes["aria-label"] = tooltip
                                        attrs.onClickFunction = {
                                            it.preventDefault()
                                            if (!loading) declareAi(!props.mapInfo.declaredAi.markAsBot)
                                        }
                                        span("dd-text") { +tooltip }
                                        i("fas " + if (props.mapInfo.declaredAi.markAsBot) "fa-user-check text-success" else "fa-user-times text-danger-light") { }
                                    }
                                    a("#") {
                                        val tooltip = if (props.mapInfo.nsfw) "Flag as safe" else "Flag as NSFW"
                                        attrs.title = tooltip
                                        attrs.attributes["aria-label"] = tooltip
                                        attrs.onClickFunction = {
                                            it.preventDefault()
                                            if (!loading) markNsfw(!props.mapInfo.nsfw)
                                        }
                                        span("dd-text") { +tooltip }
                                        i("fas fa-shield-alt " + if (props.mapInfo.nsfw) "text-success" else "text-danger-light") { }
                                    }
                                    a("#") {
                                        attrs.title = "Delete"
                                        attrs.attributes["aria-label"] = "Delete"
                                        attrs.onClickFunction = {
                                            it.preventDefault()
                                            modal?.current?.showDialog?.invoke(
                                                ModalData(
                                                    "Delete map",
                                                    bodyCallback = {
                                                        p {
                                                            +"Delete completely so that no more versions can be added or just unpublish the current version?"
                                                        }
                                                        p {
                                                            +"Reason for action:"
                                                        }
                                                        textarea(classes = "form-control") {
                                                            ref = reasonRef
                                                        }
                                                    },
                                                    buttons = listOf(
                                                        ModalButton("DELETE", "danger", ::delete),
                                                        ModalButton("Unpublish", "primary", ::recall),
                                                        ModalButton("Cancel")
                                                    )
                                                )
                                            )
                                        }
                                        span("dd-text") { +"Delete" }
                                        i("fas fa-trash text-danger-light") { }
                                    }
                                } else if (userData?.suspended == false && !isOwnerLocal) {
                                    a("#") {
                                        attrs.id = "report"
                                        attrs.title = "Report"
                                        attrs.attributes["aria-label"] = "Report"
                                        attrs.onClickFunction = {
                                            it.preventDefault()
                                            modal?.current?.showDialog?.invoke(
                                                ModalData(
                                                    "Report map",
                                                    bodyCallback = {
                                                        reportModal {
                                                            attrs.subject = "map"
                                                            attrs.reasonRef = reasonRef
                                                            attrs.captchaRef = captchaRef
                                                            attrs.errorsRef = errorRef
                                                        }
                                                    },
                                                    buttons = listOf(
                                                        ModalButton("Report", "danger", ::report),
                                                        ModalButton("Cancel")
                                                    )
                                                )
                                            )
                                        }
                                        span("dd-text") { +"Report" }
                                        i("fas fa-flag text-danger-light") { }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        div("card-body mapinfo") {
            audioPreview {
                attrs.nsfw = props.mapInfo.nsfw
                attrs.version = props.mapInfo.mainVersion()
                attrs.size = AudioPreviewSize.Large
                attrs.audio = audio
            }
            div("card-text clearfix") {
                if (editing) {
                    // If you're not an admin or the owner I hope you're a curator
                    val isCurating = !(userData?.admin == true || isOwnerLocal)
                    input(InputType.text, classes = "form-control m-2") {
                        attrs.id = "name"
                        attrs.disabled = loading || isCurating
                        ref = inputRef
                    }
                    textarea("10", classes = "form-control m-2") {
                        attrs.id = "description"
                        attrs.disabled = loading || isCurating
                        +props.mapInfo.description
                        ref = textareaRef
                    }

                    tagPicker {
                        attrs.classes = "m-2"
                        attrs.tags = tags
                        attrs.tagUpdateCallback = {
                            setTags(it)
                        }
                    }

                    if (userData?.suspended == false) {
                        collaboratorPicker {
                            attrs.classes = "m-2"
                            attrs.map = props.mapInfo
                            attrs.disabled = loading || isCurating
                        }
                    }
                } else {
                    p("text-break") {
                        textToContent(props.mapInfo.description)
                    }
                }
            }

            if (editing) {
                div("text-end") {
                    errors {
                        attrs.errors = errors
                    }

                    if (isOwnerLocal) {
                        if (props.mapInfo.publishedVersion() != null) {
                            button(classes = "btn btn-danger m-1") {
                                attrs.disabled = loading
                                attrs.onClickFunction = {
                                    modal?.current?.showDialog?.invoke(
                                        ModalData(
                                            "Are you sure?",
                                            "This will hide your map from other players until you publish a new version",
                                            listOf(ModalButton("OK", "primary", ::recall), ModalButton("Cancel"))
                                        )
                                    )
                                }
                                +"Unpublish"
                            }
                        } else {
                            button(classes = "btn btn-danger m-1") {
                                attrs.disabled = loading
                                attrs.onClickFunction = {
                                    modal?.current?.showDialog?.invoke(
                                        ModalData(
                                            "Are you sure?",
                                            "You won't be able to recover this map after you delete it",
                                            listOf(ModalButton("DELETE", "primary", ::delete), ModalButton("Cancel"))
                                        )
                                    )
                                }
                                +"Delete"
                            }
                        }
                    }
                    button(classes = "btn btn-primary m-1") {
                        attrs.disabled = loading
                        attrs.onClickFunction = {
                            val newTitle = inputRef.current?.value ?: ""
                            val newDescription = textareaRef.current?.value ?: ""
                            val newTags = tags.toList()

                            setLoading(true)

                            val update = if (userData?.admin == true || isOwnerLocal) {
                                Triple("update", MapInfoUpdate(props.mapInfo.intId(), newTitle, newDescription, newTags), generateConfig<MapInfoUpdate, String>())
                            } else {
                                Triple("tagupdate", SimpleMapInfoUpdate(props.mapInfo.intId(), newTags), generateConfig<SimpleMapInfoUpdate, String>())
                            }

                            Axios.post<String>("${Config.apibase}/maps/${update.first}", update.second, update.third).then({
                                props.updateMapinfo(props.mapInfo.copy(name = newTitle, description = newDescription, tags = newTags))
                                setLoading(false)
                                setEditing(false)
                            }) {
                                setLoading(false)
                            }
                        }
                        +"Save"
                    }
                }
            }
        }
    }
}
