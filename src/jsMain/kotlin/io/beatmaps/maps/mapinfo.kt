package io.beatmaps.maps

import external.Axios
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.BookmarkRequest
import io.beatmaps.api.CollaborationDetail
import io.beatmaps.api.CollaborationRemoveData
import io.beatmaps.api.CollaborationRequestData
import io.beatmaps.api.CurateMap
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapInfoUpdate
import io.beatmaps.api.SimpleMapInfoUpdate
import io.beatmaps.api.StateUpdate
import io.beatmaps.api.UserDetail
import io.beatmaps.api.ValidateMap
import io.beatmaps.common.MapTag
import io.beatmaps.common.MapTagType
import io.beatmaps.common.api.EMapState
import io.beatmaps.globalContext
import io.beatmaps.index.ModalButton
import io.beatmaps.index.ModalComponent
import io.beatmaps.index.ModalData
import io.beatmaps.playlist.addToPlaylist
import io.beatmaps.shared.bookmarkButton
import io.beatmaps.shared.links
import io.beatmaps.util.textToContent
import kotlinx.browser.window
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.id
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.Event
import react.Props
import react.RBuilder
import react.RComponent
import react.RefObject
import react.State
import react.createRef
import react.dom.a
import react.dom.button
import react.dom.div
import react.dom.form
import react.dom.h4
import react.dom.i
import react.dom.img
import react.dom.input
import react.dom.jsStyle
import react.dom.p
import react.dom.span
import react.dom.textarea
import react.fc
import react.setState
import kotlin.collections.set

external interface MapTagProps : Props {
    var selected: Boolean
    var excluded: Boolean
    var margins: String?
    var tag: MapTag
    var onClick: (Event) -> Unit
}

val mapTag = fc<MapTagProps> { props ->
    val dark = !props.selected && !props.excluded
    val margins = props.margins ?: "me-2 mb-2"
    span("badge badge-${if (props.excluded) "danger" else props.tag.type.color} $margins") {
        attrs.jsStyle {
            opacity = if (dark) 0.4 else 1
        }
        attrs.title = props.tag.human
        attrs.onClickFunction = props.onClick
        +props.tag.human
    }
}

external interface MapInfoProps : Props {
    var mapInfo: MapDetail
    var isOwner: Boolean
    var modal: RefObject<ModalComponent>
    var reloadMap: () -> Unit
    var deleteMap: () -> Unit
    var updateMapinfo: (MapDetail) -> Unit
}

external interface MapInfoState : State {
    var loading: Boolean?
    var editing: Boolean?
    var tags: Set<MapTag>?
}

fun interface TagPickerHeadingRenderer {
    fun RBuilder.invoke(info: Map<MapTagType, Int>)
}
external interface TagPickerProps : Props {
    var classes: String?
    var tags: Set<MapTag>?
    var tagUpdateCallback: ((Set<MapTag>) -> Unit)?
    var renderHeading: TagPickerHeadingRenderer?
}

val tagPicker = fc<TagPickerProps> { props ->
    val tags = props.tags

    div("tags " + (props.classes ?: "")) {
        fun renderTag(it: MapTag) {
            mapTag {
                attrs.selected = tags?.contains(it) == true
                attrs.tag = it
                attrs.onClick = { _ ->
                    val shouldAdd = tags == null || (!tags.contains(it) && tags.count { o -> o.type == it.type } < MapTag.maxPerType.getValue(it.type))

                    with(tags ?: setOf()) {
                        if (shouldAdd) {
                            plus(it)
                        } else {
                            minus(it)
                        }
                    }.also {
                        props.tagUpdateCallback?.invoke(it)
                    }
                }
            }
        }

        val byType = (tags?.groupBy { it.type } ?: mapOf()).mapValues {
            it.value.size
        }.withDefault { 0 }

        props.renderHeading?.let { rh ->
            with(rh) {
                this@div.invoke(byType)
            }
        } ?: run {
            h4 {
                val allocationInfo = MapTag.maxPerType.map { "${byType.getValue(it.key)}/${it.value} ${it.key.name}" }.joinToString(", ")
                +"Tags ($allocationInfo):"
            }
        }

        tags?.sortedBy { it.type.ordinal }?.forEach(::renderTag)

        MapTag.sorted.minus(tags ?: setOf()).fold(MapTagType.None) { prev, it ->
            if (it.type != prev) div("break") {}

            if (byType.getValue(it.type) < MapTag.maxPerType.getValue(it.type)) {
                renderTag(it)
            }
            it.type
        }
    }
}

external interface CollaboratorPickerProps : Props {
    var classes: String?
    var map: MapDetail
    var disabled: Boolean
}

external interface CollaboratorPickerState : State {
    var foundUsers: List<UserDetail>?
    var collaborators: List<CollaborationDetail>?
}

class CollaboratorPicker : RComponent<CollaboratorPickerProps, CollaboratorPickerState>() {
    private val inputRef = createRef<HTMLInputElement>()

    override fun componentWillMount() {
        Axios.get<List<CollaborationDetail>>(
            "${Config.apibase}/collaborations/map/${props.map.id}",
            generateConfig<String, List<CollaborationDetail>>()
        ).then {
            setState {
                collaborators = it.data
            }
        }
    }

    override fun RBuilder.render() {
        globalContext.Consumer { userData ->
            div("collaborators " + (props.classes ?: "")) {
                h4 {
                    +"Collaborators"
                }

                state.collaborators?.let { collaborationDetails ->
                    div {
                        collaborationDetails.forEach { c ->
                            div("collaborator" + if (c.accepted) " accepted" else "") {
                                img(c.collaborator.name, c.collaborator.avatar) { }
                                span {
                                    +c.collaborator.name
                                    span("status") {
                                        +(if (c.accepted) "Accepted" else "Pending")
                                    }
                                }
                                a(classes = "btn-close") {
                                    attrs.onClickFunction = {
                                        Axios.post<String>(
                                            "${Config.apibase}/collaborations/remove",
                                            CollaborationRemoveData(c.mapId, c.collaborator.id),
                                            generateConfig<CollaborationRemoveData, String>()
                                        ).then {
                                            setState {
                                                collaborators = (collaborators ?: listOf()) - c
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                form("", classes = "search") {
                    input(InputType.search, classes = "form-control") {
                        attrs.id = "collaborators"
                        attrs.placeholder = "Add users"
                        attrs.disabled = props.disabled
                        ref = inputRef
                    }

                    button(type = ButtonType.submit, classes = "btn btn-primary") {
                        attrs.onClickFunction = {
                            it.preventDefault()
                            inputRef.current?.value?.ifBlank { null }?.let { q ->
                                Axios.get<List<UserDetail>>(
                                    "${Config.apibase}/users/search?q=$q",
                                    generateConfig<String, List<UserDetail>>()
                                )
                                    .then {
                                        setState {
                                            foundUsers = it.data
                                        }
                                    }
                            } ?: setState {
                                foundUsers = null
                            }
                        }
                        +"Search"
                    }
                }

                state.foundUsers?.filter {
                    it.id != userData?.userId && state.collaborators?.none { c ->
                        c.collaborator.id == it.id
                    } ?: true
                }?.ifEmpty { null }?.let { users ->
                    div("search-results list-group") {
                        users.forEach { user ->
                            div("list-group-item user") {
                                span {
                                    img(user.name, user.avatar) { }
                                    +user.name
                                }

                                a(classes = "btn btn-success btn-sm") {
                                    attrs.onClickFunction = {
                                        Axios.post<String>(
                                            "${Config.apibase}/collaborations/request",
                                            CollaborationRequestData(props.map.intId(), user.id),
                                            generateConfig<CollaborationRequestData, String>()
                                        ).then {
                                            setState {
                                                collaborators = (collaborators ?: listOf()) + CollaborationDetail(props.map.intId(), user, false)
                                            }
                                        }
                                    }
                                    +"Invite"
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun RBuilder.collaboratorPicker(handler: CollaboratorPickerProps.() -> Unit) =
    child(CollaboratorPicker::class) {
        this.attrs(handler)
    }

class MapInfo : RComponent<MapInfoProps, MapInfoState>() {
    private val inputRef = createRef<HTMLInputElement>()
    private val textareaRef = createRef<HTMLTextAreaElement>()
    private val reasonRef = createRef<HTMLTextAreaElement>()

    override fun componentWillMount() {
        setState {
            tags = props.mapInfo.tags.toSet()
        }
    }

    private fun recall() {
        props.mapInfo.publishedVersion()?.hash?.let { hash ->
            Axios.post<String>("${Config.apibase}/testplay/state", StateUpdate(hash, EMapState.Uploaded, props.mapInfo.intId(), reasonRef.current?.value), generateConfig<StateUpdate, String>())
                .then({
                    props.reloadMap()

                    setState {
                        loading = false
                    }
                }) {
                    setState {
                        loading = false
                    }
                }
        }
    }

    private fun delete() {
        Axios.post<String>("${Config.apibase}/maps/update", MapInfoUpdate(props.mapInfo.intId(), deleted = true, reason = reasonRef.current?.value?.trim()), generateConfig<MapInfoUpdate, String>()).then({
            props.deleteMap()
        }) { }
    }

    private fun curate(curated: Boolean = true) {
        Axios.post<String>("${Config.apibase}/maps/curate", CurateMap(props.mapInfo.intId(), curated, reason = reasonRef.current?.value?.trim()), generateConfig<CurateMap, String>()).then({
            props.reloadMap()
        }) { }
    }

    private fun validate(automapper: Boolean = true) {
        Axios.post<String>("${Config.apibase}/maps/validate", ValidateMap(props.mapInfo.intId(), automapper), generateConfig<ValidateMap, String>()).then({
            props.reloadMap()
        }) { }
    }

    private fun bookmark(bookmarked: Boolean) {
        Axios.post<String>("${Config.apibase}/bookmark", BookmarkRequest(props.mapInfo.id, bookmarked = bookmarked), generateConfig<BookmarkRequest, String>()).then({
            props.reloadMap()
        }) { }
    }

    override fun RBuilder.render() {
        val deleted = props.mapInfo.deletedAt != null
        globalContext.Consumer { userData ->
            div("card") {
                div("card-header d-flex" + if (deleted) " bg-danger" else "") {
                    if (state.editing == true) {
                        +"Edit map"
                    } else {
                        +props.mapInfo.name
                    }
                    div("ms-auto flex-shrink-0") {
                        if (!deleted) {
                            props.mapInfo.mainVersion()?.let { version ->
                                if (userData != null) {
                                    addToPlaylist {
                                        map = props.mapInfo
                                        modal = props.modal
                                    }
                                    bookmarkButton {
                                        attrs.bookmarked = props.mapInfo.bookmarked == true
                                        attrs.onClick = { e, bm ->
                                            e.preventDefault()
                                            bookmark(!bm)
                                        }
                                    }
                                }

                                links {
                                    attrs.map = props.mapInfo
                                    attrs.version = version
                                    attrs.modal = props.modal
                                }

                                if (userData?.curator == true || props.isOwner) {
                                    a("#") {
                                        attrs.title = "Edit"
                                        attrs.attributes["aria-label"] = "Edit"
                                        attrs.onClickFunction = {
                                            it.preventDefault()
                                            setState {
                                                editing = state.editing != true
                                            }
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

                                if (userData?.curator == true && !props.isOwner) {
                                    a("#") {
                                        val isCurated = props.mapInfo.curator != null
                                        val text = if (isCurated) "Uncurate" else "Curate"
                                        attrs.title = text
                                        attrs.attributes["aria-label"] = text
                                        attrs.onClickFunction = {
                                            it.preventDefault()
                                            if (!isCurated) curate()
                                            else props.modal.current?.showDialog(
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
                                        i("fas fa-award " + if (isCurated) "text-danger" else "text-success") { }
                                    }
                                }
                                if (userData?.admin == true) {
                                    a("#") {
                                        attrs.title = if (props.mapInfo.automapper) "Flag as Human-made Map" else "Flag as AI-assisted Map"
                                        attrs.attributes["aria-label"] = if (props.mapInfo.automapper) "Validate" else "Invalidate"
                                        attrs.onClickFunction = {
                                            it.preventDefault()
                                            validate(!props.mapInfo.automapper)
                                        }
                                        i("fas " + if (props.mapInfo.automapper) "fa-user-check text-success" else "fa-user-times text-danger") { }
                                    }
                                    a("#") {
                                        attrs.title = "Delete"
                                        attrs.attributes["aria-label"] = "Delete"
                                        attrs.onClickFunction = {
                                            it.preventDefault()
                                            props.modal.current?.showDialog(
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
                                        i("fas fa-trash text-danger") { }
                                    }
                                }
                            }
                        }
                    }
                }
                div("card-body mapinfo") {
                    img("Cover Image", props.mapInfo.mainVersion()?.coverURL) {
                        attrs.width = "200"
                        attrs.height = "200"
                    }
                    div("card-text clearfix") {
                        if (state.editing == true) {
                            // If you're not an admin or the owner I hope you're a curator
                            val isCurating = !(userData?.admin == true || props.isOwner)
                            input(InputType.text, classes = "form-control m-2") {
                                attrs.id = "name"
                                attrs.disabled = state.loading == true || isCurating
                                ref = inputRef
                            }
                            textarea("10", classes = "form-control m-2") {
                                attrs.id = "description"
                                attrs.disabled = state.loading == true || isCurating
                                +props.mapInfo.description
                                ref = textareaRef
                            }

                            tagPicker {
                                attrs.classes = "m-2"
                                attrs.tags = state.tags
                                attrs.tagUpdateCallback = {
                                    setState {
                                        tags = it
                                    }
                                }
                            }

                            collaboratorPicker {
                                classes = "m-2"
                                map = props.mapInfo
                                disabled = state.loading == true || isCurating
                            }
                        } else {
                            textToContent(props.mapInfo.description)
                        }
                    }

                    if (state.editing == true) {
                        div("text-end") {
                            if (props.isOwner) {
                                if (props.mapInfo.publishedVersion() != null) {
                                    a(classes = "btn btn-danger m-1") {
                                        attrs.onClickFunction = {
                                            props.modal.current?.showDialog(
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
                                    a(classes = "btn btn-danger m-1") {
                                        attrs.onClickFunction = {
                                            props.modal.current?.showDialog(
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
                            a(classes = "btn btn-primary m-1") {
                                attrs.onClickFunction = {
                                    val newTitle = inputRef.current?.value ?: ""
                                    val newDescription = textareaRef.current?.value ?: ""
                                    val newTags = state.tags?.toList()

                                    setState {
                                        loading = true
                                    }

                                    val update = if (userData?.admin == true || props.isOwner) {
                                        Triple("update", MapInfoUpdate(props.mapInfo.intId(), newTitle, newDescription, newTags), generateConfig<MapInfoUpdate, String>())
                                    } else {
                                        Triple("tagupdate", SimpleMapInfoUpdate(props.mapInfo.intId(), newTags), generateConfig<SimpleMapInfoUpdate, String>())
                                    }

                                    Axios.post<String>("${Config.apibase}/maps/${update.first}", update.second, update.third).then({
                                        props.updateMapinfo(props.mapInfo.copy(name = newTitle, description = newDescription, tags = newTags ?: listOf()))
                                        setState {
                                            loading = false
                                            editing = false
                                        }
                                    }) {
                                        setState {
                                            loading = false
                                        }
                                    }
                                }
                                +"Save"
                            }
                        }
                    }
                }
            }
        }
    }
}

fun RBuilder.mapInfo(handler: MapInfoProps.() -> Unit) =
    child(MapInfo::class) {
        this.attrs(handler)
    }
