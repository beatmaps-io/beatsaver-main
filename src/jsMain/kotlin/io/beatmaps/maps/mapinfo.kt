package io.beatmaps.maps

import Axios
import generateConfig
import io.beatmaps.api.CurateMap
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapInfoUpdate
import io.beatmaps.api.StateUpdate
import io.beatmaps.common.Config
import io.beatmaps.common.api.EMapState
import io.beatmaps.index.ModalButton
import io.beatmaps.index.ModalComponent
import io.beatmaps.index.ModalData
import io.beatmaps.index.oneclick
import io.beatmaps.maps.testplay.JsonRequestConfig
import kotlinx.browser.window
import kotlinx.html.InputType
import kotlinx.html.Tag
import kotlinx.html.id
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.get
import react.*
import react.dom.*
import kotlin.collections.set

external interface MapInfoProps : RProps {
    var mapInfo: MapDetail
    var isOwner: Boolean
    var modal: RReadableRef<ModalComponent>
    var reloadMap: () -> Unit
    var deleteMap: () -> Unit
}

data class MapInfoState(var loading: Boolean = false) : RState

@JsExport
class MapInfo : RComponent<MapInfoProps, MapInfoState>() {
    private val inputRef = createRef<HTMLInputElement>()
    private val textareaRef = createRef<HTMLTextAreaElement>()

    init {
        state = MapInfoState()
    }

    override fun componentDidMount() {
        if (props.isOwner) {
            inputRef.current?.value = props.mapInfo.name
        }
    }

    private fun recall() {
        props.mapInfo.publishedVersion()?.hash?.let { hash ->
            Axios.post<String>("/api/testplay/state", StateUpdate(hash, EMapState.Uploaded), generateConfig<StateUpdate, String>())
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
        Axios.post<String>("/api/maps/update", MapInfoUpdate(props.mapInfo.id, deleted = true), generateConfig<MapInfoUpdate, String>()).then({
            props.deleteMap()
        }) {
            setState {
                loading = false
            }
        }
    }

    private fun curate(curated: Boolean = true) {
        Axios.post<String>("/api/maps/curate", CurateMap(props.mapInfo.id, curated), generateConfig<CurateMap, String>()).then({
            props.reloadMap()
        }) {
            setState {
                loading = false
            }
        }
    }

    override fun RBuilder.render() {
        div("card") {
            div("card-header d-flex") {
                if (props.isOwner) {
                    +"Edit map"
                } else {
                    +props.mapInfo.name
                }
                div("ml-auto") {
                    props.mapInfo.latestVersion()?.let { version ->
                        a("${Config.cdnbase}/${version.hash}.zip", target = "_blank") {
                            attrs.title = "Download zip"
                            attrs.attributes["aria-label"] = "Download zip"
                            i("fas fa-download text-info") { }
                        }
                        if (Config.oneClick) {
                            version.key?.let { key64 ->
                                a("https://beatsaver.com/beatmap/$key64", target = "_blank") {
                                    attrs.title = key64
                                    attrs.attributes["aria-label"] = key64
                                    i("fas fa-share-alt text-info") { }
                                }
                            }
                        }
                        a("#") {
                            attrs.title = "Preview"
                            attrs.attributes["aria-label"] = "Preview"
                            attrs.onClickFunction = {
                                it.preventDefault()
                                props.modal.current?.show(version.hash)
                            }
                            i("fas fa-play text-info") { }
                        }
                        oneclick {
                            mapId = props.mapInfo.id
                            modal = props.modal
                        }

                        val adminLocal = window["admin"] as Boolean?

                        if (adminLocal == true) {
                            a("#") {
                                attrs.title = "Curate"
                                attrs.attributes["aria-label"] = "Curate"
                                attrs.onClickFunction = {
                                    it.preventDefault()
                                    curate(props.mapInfo.curator == null)
                                }
                                i("fas fa-award " + if (props.mapInfo.curator != null) "text-danger" else "text-success") { }
                            }
                            a("#") {
                                attrs.title = "Delete"
                                attrs.attributes["aria-label"] = "Delete"
                                attrs.onClickFunction = {
                                    it.preventDefault()
                                    props.modal.current?.showDialog(ModalData(
                                        "Delete map",
                                        "Delete completely so that no more versions can be added or just unpublish the current version?",
                                        listOf(ModalButton("DELETE", "danger", ::delete), ModalButton("Unpublish", "primary", ::recall), ModalButton("Cancel"))
                                    ))
                                }
                                i("fas fa-trash text-danger") { }
                            }
                        }
                    }
                    /*a("#") {
                        i("fab fa-twitch text-info") { }
                    }*/
                }
            }
            div("card-body mapinfo") {
                img("Cover Image", "${Config.cdnbase}/${props.mapInfo.versions.first().hash}.jpg") {
                    attrs.width = "200"
                    attrs.height = "200"
                }
                div("card-text clearfix") {
                    if (props.isOwner) {
                        input(InputType.text, classes ="form-control m-2") {
                            attrs.id = "name"
                            attrs.disabled = state.loading
                            ref = inputRef
                        }
                        textArea("10", classes ="form-control m-2") {
                            attrs.id = "description"
                            attrs.disabled = state.loading
                            +props.mapInfo.description
                            ref = textareaRef
                        }
                    } else {
                        textToContent(props.mapInfo.description)
                    }
                }

                if (props.isOwner) {
                    div("text-right") {
                        if (props.mapInfo.publishedVersion() != null) {
                            a(classes = "btn btn-danger m-1") {
                                attrs.onClickFunction = {
                                    props.modal.current?.showDialog(ModalData(
                                        "Are you sure?",
                                        "This will hide your map from other players until you publish a new version",
                                        listOf(ModalButton("OK", "primary", ::recall), ModalButton("Cancel"))
                                    ))
                                }
                                +"Recall"
                            }
                        } else {
                            a(classes = "btn btn-danger m-1") {
                                attrs.onClickFunction = {
                                    props.modal.current?.showDialog(ModalData(
                                        "Are you sure?",
                                        "You won't be able to recover this map after you delete it",
                                        listOf(ModalButton("DELETE", "primary", ::delete), ModalButton("Cancel"))
                                    ))
                                }
                                +"Delete"
                            }
                        }
                        a(classes = "btn btn-primary m-1") {
                            attrs.onClickFunction = {
                                val newTitle = inputRef.current?.value ?: ""
                                val newDescription = textareaRef.current?.asDynamic().value as String

                                setState {
                                    loading = true
                                }

                                Axios.post<String>("/api/maps/update", MapInfoUpdate(props.mapInfo.id, newTitle, newDescription), generateConfig<MapInfoUpdate, String>()).then({
                                    setState {
                                        loading = false
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

fun String.transformURLIntoLinks() =
    replace("\\b((https?|ftp):\\/\\/)?[-a-zA-Z0-9@:%._\\+~#=]{2,256}\\.[A-Za-z]{2,6}\\b(\\/[-a-zA-Z0-9@:%_\\+.~#?&//=]*)*(?:\\/|\\b)".toRegex()) {
        if (it.groupValues[1].isEmpty()) it.value else "<a target=\"_blank\" href=\"${it.value}\">${it.value}</a>"
    }

fun RBuilder.mapInfo(handler: MapInfoProps.() -> Unit): ReactElement {
    return child(MapInfo::class) {
        this.attrs(handler)
    }
}

// Kotlin IR be adding underscores everywhere
data class DangerousHtml(val _html: String)

fun <T: Tag> RDOMBuilder<T>.textToContent(text: String) {
    setProp("dangerouslySetInnerHTML", DangerousHtml(
        text
            .replace(Regex("<[^>]+>"), "")
            .transformURLIntoLinks()
            .replace("\n", "<br />")
    ))
}