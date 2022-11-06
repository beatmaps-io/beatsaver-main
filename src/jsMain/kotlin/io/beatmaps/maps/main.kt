package io.beatmaps.maps

import external.axiosGet
import io.beatmaps.api.LeaderboardType
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapDifficulty
import io.beatmaps.api.ReviewConstants
import io.beatmaps.common.Config
import io.beatmaps.globalContext
import io.beatmaps.index.ModalComponent
import io.beatmaps.index.modal
import io.beatmaps.maps.review.reviewTable
import io.beatmaps.maps.testplay.testplay
import io.beatmaps.setPageTitle
import kotlinx.browser.localStorage
import kotlinx.browser.window
import org.w3c.dom.get
import org.w3c.dom.set
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
    var type: LeaderboardType?
    var comments: Boolean?
}

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
                        val leaderBoardType = state.type ?: LeaderboardType.fromName(localStorage["maps.leaderboardType"]) ?: LeaderboardType.ScoreSaber
                        val showComments = ReviewConstants.COMMENTS_ENABLED && state.comments ?: (localStorage["maps.showComments"] == "true")
                        div("col-lg-4 text-nowrap") {
                            mapPageNav {
                                attrs.map = it
                                attrs.comments = showComments
                                attrs.setComments = {
                                    localStorage["maps.showComments"] = "true"
                                    setState {
                                        comments = true
                                    }
                                }
                                attrs.type = leaderBoardType
                                attrs.setType = { lt ->
                                    localStorage["maps.leaderboardType"] = lt.name
                                    localStorage["maps.showComments"] = "false"
                                    setState {
                                        type = lt
                                        comments = false
                                    }
                                }
                            }

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

                        if (showComments) {
                            reviewTable {
                                map = it.id
                                modal = modalRef
                            }
                        } else if (version != null && it.deletedAt == null) {
                            scoreTable {
                                mapKey = version.hash
                                selected = state.selectedDiff
                                type = leaderBoardType
                            }
                        }
                    }
                }
            }
        }
    }
}
