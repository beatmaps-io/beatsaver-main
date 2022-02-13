package io.beatmaps.maps

import external.Axios
import external.generateConfig
import io.beatmaps.api.CurateMap
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapInfoUpdate
import io.beatmaps.api.SimpleMapInfoUpdate
import io.beatmaps.api.StateUpdate
import io.beatmaps.common.Config
import io.beatmaps.common.MapTag
import io.beatmaps.common.MapTagType
import io.beatmaps.common.api.EMapState
import io.beatmaps.globalContext
import io.beatmaps.index.ModalButton
import io.beatmaps.index.ModalComponent
import io.beatmaps.index.ModalData
import io.beatmaps.index.links
import io.beatmaps.playlist.addToPlaylist
import kotlinx.browser.window
import kotlinx.html.InputType
import kotlinx.html.Tag
import kotlinx.html.id
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.Event
import react.RBuilder
import react.RComponent
import react.RProps
import react.RReadableRef
import react.RState
import react.ReactElement
import react.createRef
import react.dom.InnerHTML
import react.dom.RDOMBuilder
import react.dom.a
import react.dom.div
import react.dom.h4
import react.dom.i
import react.dom.img
import react.dom.input
import react.dom.jsStyle
import react.dom.p
import react.dom.span
import react.dom.textarea
import react.functionComponent
import react.setState
import kotlin.collections.set

external interface MapTagProps : RProps {
    var selected: Boolean
    var margins: String?
    var tag: MapTag
    var onClick: (Event) -> Unit
}

val mapTag = functionComponent<MapTagProps> { props ->
    val dark = !props.selected
    val margins = props.margins ?: "mr-2 mb-2"
    span("badge badge-${props.tag.type.color} $margins") {
        attrs.jsStyle {
            opacity = if (dark) 0.4 else 1
        }
        attrs.title = props.tag.human
        attrs.onClickFunction = props.onClick
        +props.tag.human
    }
}

external interface MapInfoProps : RProps {
    var mapInfo: MapDetail
    var isOwner: Boolean
    var modal: RReadableRef<ModalComponent>
    var reloadMap: () -> Unit
    var deleteMap: () -> Unit
    var updateMapinfo: (MapDetail) -> Unit
}

external interface MapInfoState : RState {
    var loading: Boolean?
    var editing: Boolean?
    var tags: Set<MapTag>?
}

@JsExport
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
        Axios.post<String>("${Config.apibase}/maps/update", MapInfoUpdate(props.mapInfo.intId(), deleted = true, reason = reasonRef.current?.value), generateConfig<MapInfoUpdate, String>()).then({
            props.deleteMap()
        }) { }
    }

    private fun curate(curated: Boolean = true) {
        Axios.post<String>("${Config.apibase}/maps/curate", CurateMap(props.mapInfo.intId(), curated), generateConfig<CurateMap, String>()).then({
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
                    div("ml-auto flex-shrink-0") {
                        if (!deleted) {
                            props.mapInfo.mainVersion()?.let { version ->
                                if (userData != null) {
                                    addToPlaylist {
                                        map = props.mapInfo
                                        modal = props.modal
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

                                if (userData?.curator == true) {
                                    a("#") {
                                        attrs.title = "Curate"
                                        attrs.attributes["aria-label"] = "Curate"
                                        attrs.onClickFunction = {
                                            it.preventDefault()
                                            curate(props.mapInfo.curator == null)
                                        }
                                        i("fas fa-award " + if (props.mapInfo.curator != null) "text-danger" else "text-success") { }
                                    }
                                }
                                if (userData?.admin == true) {
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

                            div("tags") {
                                fun renderTag(it: MapTag) {
                                    mapTag {
                                        attrs.selected = state.tags?.contains(it) == true
                                        attrs.tag = it
                                        attrs.onClick = { _ ->
                                            val stateTags = state.tags
                                            val shouldAdd = stateTags == null || (!stateTags.contains(it) && stateTags.count { o -> o.type == it.type } < MapTag.maxPerType.getValue(it.type))

                                            val newTags = with(state.tags ?: setOf()) {
                                                if (shouldAdd) {
                                                    plus(it)
                                                } else {
                                                    minus(it)
                                                }
                                            }

                                            setState {
                                                tags = newTags
                                            }
                                        }
                                    }
                                }

                                val byType = (state.tags?.groupBy { it.type } ?: mapOf()).mapValues {
                                    it.value.size
                                }.withDefault { 0 }

                                h4 {
                                    val allocationInfo = MapTag.maxPerType.map { "${byType.getValue(it.key)}/${it.value} ${it.key.name}" }.joinToString(", ")
                                    +"Tags ($allocationInfo):"
                                }
                                state.tags?.sortedBy { it.type.ordinal }?.forEach(::renderTag)

                                MapTag.sorted.minus(state.tags ?: setOf()).fold(MapTagType.None) { prev, it ->
                                    if (it.type != prev) div("break") {}

                                    if (byType.getValue(it.type) < MapTag.maxPerType.getValue(it.type)) {
                                        renderTag(it)
                                    }
                                    it.type
                                }
                            }
                        } else {
                            textToContent(props.mapInfo.description)
                        }
                    }

                    if (state.editing == true) {
                        div("text-right") {
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
                                    val newDescription = textareaRef.current?.asDynamic().value as String
                                    val newTags = state.tags?.map { it.slug }

                                    setState {
                                        loading = true
                                    }

                                    val update = if (userData?.admin == true || props.isOwner) {
                                        Triple("update", MapInfoUpdate(props.mapInfo.intId(), newTitle, newDescription, newTags), generateConfig<MapInfoUpdate, String>())
                                    } else {
                                        Triple("tagupdate", SimpleMapInfoUpdate(props.mapInfo.intId(), newTags), generateConfig<SimpleMapInfoUpdate, String>())
                                    }

                                    Axios.post<String>("${Config.apibase}/maps/${update.first}", update.second, update.third).then({
                                        props.updateMapinfo(props.mapInfo.copy(name = newTitle, description = newDescription))
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

fun String.transformURLIntoLinks() =
    replace("\\b((https?|ftp):\\/\\/)?[-a-zA-Z0-9@:%._\\+~#=]{2,256}\\.[A-Za-z]{2,6}\\b(\\/[-a-zA-Z0-9@:%_\\+.~#?&//=]*)*(?:\\/|\\b)".toRegex()) {
        if (it.groupValues[1].isEmpty()) it.value else "<a target=\"_blank\" href=\"${it.value}\">${it.value}</a>"
    }

fun String.parseBoldMarkdown() =
    replace("(^| )(\\*\\*|__)(.*?)\\2".toRegex(RegexOption.MULTILINE)) {
        "${it.groupValues[1]}<b>${it.groupValues[3]}</b>"
    }

fun String.parseItalicMarkdown() =
    replace("(^| )(\\*|_)(.*?)\\2".toRegex(RegexOption.MULTILINE)) {
        "${it.groupValues[1]}<i>${it.groupValues[3]}</i>"
    }

fun RBuilder.mapInfo(handler: MapInfoProps.() -> Unit): ReactElement {
    return child(MapInfo::class) {
        this.attrs(handler)
    }
}

// Kotlin IR be adding underscores everywhere
class DangerousHtml(override var __html: String) : InnerHTML

fun <T : Tag> RDOMBuilder<T>.textToContent(text: String) {
    domProps.dangerouslySetInnerHTML = DangerousHtml(
        text
            .replace(Regex("<[^>]+>"), "")
            .parseBoldMarkdown()
            .parseItalicMarkdown()
            .transformURLIntoLinks()
            .replace("\n", "<br />")
    )
}
