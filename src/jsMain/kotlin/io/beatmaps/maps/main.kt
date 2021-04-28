package io.beatmaps.maps

import io.beatmaps.common.Config
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapDifficulty
import io.beatmaps.index.ModalComponent
import io.beatmaps.index.modal
import io.beatmaps.maps.testplay.testplay
import io.beatmaps.setPageTitle
import kotlinx.browser.window
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.w3c.dom.get
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.createRef
import react.dom.*
import react.ref
import react.router.dom.RouteResultHistory
import react.setState

external interface MapPageProps : RProps {
    var history: RouteResultHistory
    var mapKey: String
    var beatsaver: Boolean
}

external interface MapPageState : RState {
    var map: MapDetail?
    var selectedDiff: MapDifficulty?
}

@JsExport
class MapPage : RComponent<MapPageProps, MapPageState>() {
    private val modalRef = createRef<ModalComponent>()

    override fun componentDidMount() {
        setPageTitle("Map")

        loadMap()
    }

    private fun loadMap() {
        val subPath = if (props.beatsaver) {
            "beatsaver"
        } else {
            "id"
        }

        window.fetch("${Config.apibase}/maps/$subPath/${props.mapKey}")
            .then {
                it.text()
            }.then {
                val mapLocal = Json.decodeFromString<MapDetail>(it)
                setPageTitle("Map - " + mapLocal.name)
                setState {
                    map = mapLocal
                    selectedDiff = mapLocal.publishedVersion()?.diffs?.sortedWith(compareBy<MapDifficulty> { d -> d.characteristic }.thenByDescending { d -> d.difficulty })?.first()
                }
            }.catch {
                props.history.push("/")
            }
    }

    override fun RBuilder.render() {
        state.map?.let {
            val version = it.publishedVersion()
            val isOwnerLocal = window["userId"] as Int? == it.uploader.id
            val loggedInLocal = window["userId"] as Int?

            if (version == null) {
                testplay {
                    mapInfo = it
                    isOwner = isOwnerLocal
                    loggedInId = loggedInLocal
                    refreshPage = {
                        loadMap()
                        window.scrollTo(0.0, 0.0)
                    }
                    history = props.history
                }
            } else {
                modal {
                    ref = modalRef
                }

                mapInfo {
                    mapInfo = it
                    isOwner = isOwnerLocal
                    modal = modalRef
                    reloadMap = ::loadMap
                    deleteMap = {
                        props.history.push("/profile")
                    }
                }
                div("row mt-3") {
                    infoTable {
                        map = it
                        selected = state.selectedDiff
                        changeSelectedDiff = {
                            setState {
                                selectedDiff = it
                            }
                        }
                    }
                    scoreTable {
                        mapKey = version.hash
                        selected = state.selectedDiff
                    }
                }
            }
        }
    }
}