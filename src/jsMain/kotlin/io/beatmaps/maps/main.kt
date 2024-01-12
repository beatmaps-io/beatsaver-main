package io.beatmaps.maps

import external.axiosGet
import io.beatmaps.Config
import io.beatmaps.History
import io.beatmaps.api.LeaderboardType
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapDifficulty
import io.beatmaps.api.ReviewConstants
import io.beatmaps.index.ModalComponent
import io.beatmaps.index.modal
import io.beatmaps.index.modalContext
import io.beatmaps.maps.testplay.testplay
import io.beatmaps.setPageTitle
import io.beatmaps.shared.review.reviewTable
import kotlinx.browser.localStorage
import kotlinx.browser.window
import org.w3c.dom.get
import org.w3c.dom.set
import react.Props
import react.dom.div
import react.fc
import react.ref
import react.router.useNavigate
import react.router.useParams
import react.useEffect
import react.useEffectOnce
import react.useRef
import react.useState

external interface MapPageProps : Props {
    var beatsaver: Boolean
}

val mapPage = fc<MapPageProps> { props ->
    val (map, setMap) = useState<MapDetail?>(null)
    val (selectedDiff, setSelectedDiff) = useState<MapDifficulty?>(null)
    val (type, setType) = useState<LeaderboardType?>(null)
    val (comments, setComments) = useState<Boolean?>(null)

    val modalRef = useRef<ModalComponent>()
    val params = useParams()
    val history = History(useNavigate())

    fun loadMap() {
        val mapKey = params["mapKey"]
        val subPath = if (props.beatsaver) "beatsaver" else "id"

        setMap(null)
        setSelectedDiff(null)
        setType(null)
        setComments(null)

        axiosGet<MapDetail>(
            "${Config.apibase}/maps/$subPath/$mapKey"
        ).then {
            val mapLocal = it.data
            setPageTitle("Map - " + mapLocal.name)
            setMap(mapLocal)
            setSelectedDiff(mapLocal.publishedVersion()?.diffs?.sortedWith(compareBy<MapDifficulty> { d -> d.characteristic }.thenByDescending { d -> d.difficulty })?.first())
        }.catch {
            history.push("/")
        }
    }

    useEffectOnce {
        setPageTitle("Map")
    }

    useEffect(params["mapKey"]) {
        loadMap()
    }

    map?.let {
        val version = it.publishedVersion()

        if (version == null && it.deletedAt == null) {
            testplay {
                attrs.mapInfo = it
                attrs.refreshPage = {
                    loadMap()
                    window.scrollTo(0.0, 0.0)
                }
                attrs.history = history
                attrs.updateMapinfo = { map ->
                    setMap(map)
                }
            }
        } else {
            modal {
                ref = modalRef
            }

            modalContext.Provider {
                attrs.value = modalRef

                mapInfo {
                    attrs {
                        mapInfo = it
                        reloadMap = ::loadMap
                        deleteMap = {
                            history.push("/profile")
                        }
                        updateMapinfo = { map ->
                            setMap(map)
                        }
                    }
                }
                div("row mt-3") {
                    val leaderBoardType = type ?: LeaderboardType.fromName(localStorage["maps.leaderboardType"]) ?: LeaderboardType.ScoreSaber
                    val showComments = ReviewConstants.COMMENTS_ENABLED && comments ?: (localStorage["maps.showComments"] == "true")
                    div("col-lg-4 text-nowrap") {
                        infoTable {
                            attrs.map = it
                            attrs.selected = selectedDiff
                            attrs.changeSelectedDiff = { diff ->
                                setSelectedDiff(diff)
                            }
                        }
                    }

                    div("col-lg-8 text-nowrap") {
                        mapPageNav {
                            attrs.map = it
                            attrs.comments = showComments
                            attrs.setComments = {
                                localStorage["maps.showComments"] = "true"
                                setComments(true)
                            }
                            attrs.type = leaderBoardType
                            attrs.setType = { lt ->
                                localStorage["maps.leaderboardType"] = lt.name
                                localStorage["maps.showComments"] = "false"
                                setType(lt)
                                setComments(false)
                            }
                        }

                        if (showComments) {
                            reviewTable {
                                attrs.map = it.id
                                attrs.mapUploaderId = it.uploader.id
                            }
                        } else if (version != null && it.deletedAt == null) {
                            scoreTable {
                                attrs.mapKey = version.hash
                                attrs.selected = selectedDiff
                                attrs.type = leaderBoardType
                            }
                        }
                    }
                }
            }
        }
    }
}
