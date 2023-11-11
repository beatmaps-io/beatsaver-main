package io.beatmaps.maps

import external.Axios
import external.AxiosResponse
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.BookmarkRequest
import io.beatmaps.api.CurateMap
import io.beatmaps.api.ErrorResponse
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapInfoUpdate
import io.beatmaps.api.SimpleMapInfoUpdate
import io.beatmaps.api.StateUpdate
import io.beatmaps.api.ValidateMap
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.json
import io.beatmaps.globalContext
import io.beatmaps.index.ModalButton
import io.beatmaps.index.ModalData
import io.beatmaps.index.modalContext
import io.beatmaps.playlist.addToPlaylist
import io.beatmaps.shared.AudioPreviewSize
import io.beatmaps.shared.audioPreview
import io.beatmaps.shared.form.errors
import io.beatmaps.shared.map.bookmarkButton
import io.beatmaps.shared.map.links
import io.beatmaps.util.textToContent
import io.beatmaps.util.useAudio
import kotlinx.browser.window
import kotlinx.html.InputType
import kotlinx.html.id
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import kotlinx.serialization.decodeFromString
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import react.Props
import react.dom.a
import react.dom.button
import react.dom.div
import react.dom.i
import react.dom.input
import react.dom.p
import react.dom.textarea
import react.fc
import react.useContext
import react.useRef
import react.useState
import kotlin.collections.set

external interface MapInfoProps : Props {
    var mapInfo: MapDetail
    var reloadMap: () -> Unit
    var deleteMap: () -> Unit
    var updateMapinfo: (MapDetail) -> Unit
}

val mapInfo = fc<MapInfoProps> { props ->
    val inputRef = useRef<HTMLInputElement>()
    val textareaRef = useRef<HTMLTextAreaElement>()
    val reasonRef = useRef<HTMLTextAreaElement>()
    val audio = useAudio()

    val (tags, setTags) = useState(props.mapInfo.tags.toSet())
    val (loading, setLoading) = useState(false)
    val (error, setError) = useState<String?>(null)
    val (editing, setEditing) = useState(false)

    val userData = useContext(globalContext)
    val loggedInId = userData?.userId
    val isOwnerLocal = loggedInId == props.mapInfo.uploader.id

    val modal = useContext(modalContext)

    @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
    fun recall() {
        setLoading(true)

        props.mapInfo.publishedVersion()?.hash?.let { hash ->
            Axios.post<String>("${Config.apibase}/testplay/state", StateUpdate(hash, EMapState.Uploaded, props.mapInfo.intId(), reasonRef.current?.value), generateConfig<StateUpdate, String>())
                .then({
                    props.reloadMap()
                }) {
                    val response = it.asDynamic().response as? AxiosResponse<String>
                    if (response?.status == 400) {
                        setError(json.decodeFromString<ErrorResponse>(response.data).error)
                    }

                    setLoading(false)
                }
        }
    }

    fun delete() {
        setLoading(true)

        Axios.post<String>("${Config.apibase}/maps/update", MapInfoUpdate(props.mapInfo.intId(), deleted = true, reason = reasonRef.current?.value?.trim()), generateConfig<MapInfoUpdate, String>()).then({
            props.deleteMap()
        }) {
            setLoading(false)
        }
    }

    fun curate(curated: Boolean = true) {
        setLoading(true)

        Axios.post<String>("${Config.apibase}/maps/curate", CurateMap(props.mapInfo.intId(), curated, reason = reasonRef.current?.value?.trim()), generateConfig<CurateMap, String>()).then({
            props.reloadMap()
        }) {
            setLoading(false)
        }
    }

    fun validate(automapper: Boolean = true) {
        setLoading(true)

        Axios.post<String>("${Config.apibase}/maps/validate", ValidateMap(props.mapInfo.intId(), automapper), generateConfig<ValidateMap, String>()).then({
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

    val deleted = props.mapInfo.deletedAt != null

    div("card") {
        div("card-header d-flex" + if (deleted) " bg-danger" else "") {
            if (editing) {
                +"Edit map"
            } else {
                +props.mapInfo.name
            }
            div("ms-auto flex-shrink-0") {
                if (!deleted) {
                    props.mapInfo.mainVersion()?.let { version ->
                        if (userData != null) {
                            addToPlaylist {
                                attrs.map = props.mapInfo
                            }
                            bookmarkButton {
                                attrs.bookmarked = props.mapInfo.bookmarked == true
                                attrs.onClick = { e, bm ->
                                    e.preventDefault()
                                    if (!loading) bookmark(!bm)
                                }
                            }
                        }

                        links {
                            attrs.map = props.mapInfo
                            attrs.version = version
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
                                i("fas fa-pen text-warning") { }
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
                                        modal?.current?.showDialog(
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
                                        modal?.current?.showDialog(
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
                                i("fas fa-award " + if (isCurated) "text-danger-light" else "text-success") { }
                            }
                        }
                        if (userData?.admin == true) {
                            a("#") {
                                attrs.title = if (props.mapInfo.automapper) "Flag as Human-made Map" else "Flag as AI-assisted Map"
                                attrs.attributes["aria-label"] = if (props.mapInfo.automapper) "Validate" else "Invalidate"
                                attrs.onClickFunction = {
                                    it.preventDefault()
                                    if (!loading) validate(!props.mapInfo.automapper)
                                }
                                i("fas " + if (props.mapInfo.automapper) "fa-user-check text-success" else "fa-user-times text-danger-light") { }
                            }
                            a("#") {
                                attrs.title = "Delete"
                                attrs.attributes["aria-label"] = "Delete"
                                attrs.onClickFunction = {
                                    it.preventDefault()
                                    modal?.current?.showDialog(
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
                                i("fas fa-trash text-danger-light") { }
                            }
                        }
                    }
                }
            }
        }
        div("card-body mapinfo") {
            audioPreview {
                version = props.mapInfo.mainVersion()
                size = AudioPreviewSize.Large
                this.audio = audio
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
                    textToContent(props.mapInfo.description)
                }
            }

            if (editing) {
                div("text-end") {
                    error?.let {
                        errors {
                            attrs.errors = listOf(it)
                        }
                    }

                    if (isOwnerLocal) {
                        if (props.mapInfo.publishedVersion() != null) {
                            button(classes = "btn btn-danger m-1") {
                                attrs.disabled = loading
                                attrs.onClickFunction = {
                                    modal?.current?.showDialog(
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
                                    modal?.current?.showDialog(
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
