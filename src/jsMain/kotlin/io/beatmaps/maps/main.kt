package io.beatmaps.maps

import external.axiosGet
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapDifficulty
import io.beatmaps.common.Config
import io.beatmaps.globalContext
import io.beatmaps.index.ModalComponent
import io.beatmaps.index.modal
import io.beatmaps.maps.testplay.testplay
import io.beatmaps.setPageTitle
import kotlinx.browser.window
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.createRef
import react.dom.div
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

        axiosGet<MapDetail>(
            "${Config.apibase}/maps/$subPath/${props.mapKey}",
        ).then {
            val mapLocal = it.data
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
        globalContext.Consumer { userData ->
            state.map?.let {
                val version = it.publishedVersion()
                val loggedInLocal = userData?.userId
                val isOwnerLocal = loggedInLocal == it.uploader.id

                if (version == null && it.deletedAt == null) {
                    testplay {
                        mapInfo = it
                        isOwner = isOwnerLocal
                        isAdmin = userData?.admin
                        loggedInId = loggedInLocal
                        refreshPage = {
                            loadMap()
                            window.scrollTo(0.0, 0.0)
                        }
                        history = props.history
                        updateMapinfo = {
                            setState {
                                map = it
                            }
                        }
                    }
                } else {
                    modal {
                        ref = modalRef
                    }

                    mapInfo {
                        mapInfo = it
                        isOwner = isOwnerLocal
                        isAdmin = userData?.admin
                        modal = modalRef
                        reloadMap = ::loadMap
                        deleteMap = {
                            props.history.push("/profile")
                        }
                        updateMapinfo = {
                            setState {
                                map = it
                            }
                        }
                    }
                    div("row mt-3") {
                        div("col-lg-4 text-nowrap") {
                            infoTable {
                                map = it
                                selected = state.selectedDiff
                                changeSelectedDiff = {
                                    setState {
                                        selectedDiff = it
                                    }
                                }
                            }
                        }
                        if (version != null && it.deletedAt == null) {
                            scoreTable {
                                mapKey = version.hash
                                selected = state.selectedDiff
                            }
                        }
                    }
                }
            }
        }
    }
}
