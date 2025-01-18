package io.beatmaps.maps

import external.axiosGet
import io.beatmaps.Config
import io.beatmaps.History
import io.beatmaps.api.LeaderboardType
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapDifficulty
import io.beatmaps.maps.testplay.testplay
import io.beatmaps.playlist.playlists
import io.beatmaps.setPageTitle
import io.beatmaps.shared.ModalCallbacks
import io.beatmaps.shared.loadingElem
import io.beatmaps.shared.modal
import io.beatmaps.shared.modalContext
import io.beatmaps.shared.review.reviewTable
import io.beatmaps.util.fcmemo
import io.beatmaps.util.get
import io.beatmaps.util.set
import io.beatmaps.util.useDidUpdateEffect
import react.Props
import react.Suspense
import react.dom.html.ReactHTML.div
import react.router.useNavigate
import react.router.useParams
import react.useCallback
import react.useEffect
import react.useEffectOnce
import react.useRef
import react.useState
import web.cssom.ClassName
import web.storage.localStorage
import web.window.window

enum class MapTabs(val id: String, val enabled: Boolean = true) {
    ScoreSaber("ss"), BeatLeader("bl"), Reviews("rv"), Playlists("pl", enabled = false);

    companion object {
        private val map = MapTabs.entries.associateBy(MapTabs::name)
        fun fromName(name: String?) = map[name]
    }
}

external interface MapPageProps : Props {
    var beatsaver: Boolean
}

val mapPage = fcmemo<MapPageProps>("mapPage") { props ->
    val (map, setMap) = useState<MapDetail?>(null)
    val (selectedDiff, setSelectedDiff) = useState<MapDifficulty?>(null)

    fun fromLegacy(): MapTabs {
        val leaderBoardType = LeaderboardType.fromName(localStorage["maps.leaderboardType"]) ?: LeaderboardType.ScoreSaber
        val showComments = localStorage["maps.showComments"] == "true"
        val showPlaylists = localStorage["maps.showPlaylists"] == "true"

        return when {
            showComments -> MapTabs.Reviews
            showPlaylists -> MapTabs.Playlists
            leaderBoardType == LeaderboardType.BeatLeader -> MapTabs.BeatLeader
            else -> MapTabs.ScoreSaber
        }
    }

    val localStorageTab = MapTabs.fromName(localStorage["maps.selectedTab"]) ?: fromLegacy()
    val (tab, setTab) = useState(localStorageTab)

    val modalRef = useRef<ModalCallbacks>()
    val params = useParams()
    val history = History(useNavigate())

    useDidUpdateEffect(tab) {
        localStorage["maps.selectedTab"] = tab.name
    }

    val loadMap: () -> Unit = useCallback(params, props.beatsaver) {
        val mapKey = params["mapKey"]
        val subPath = if (props.beatsaver) "beatsaver" else "id"

        setMap(null)
        setSelectedDiff(null)

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

    val changeDiff = useCallback { diff: MapDifficulty ->
        setSelectedDiff(diff)
    }

    val setMapCb = useCallback { mapLocal: MapDetail ->
        setMap(mapLocal)
    }

    val reloadPage = useCallback {
        loadMap()
        window.scrollTo(0.0, 0.0)
    }

    map?.let {
        val version = it.publishedVersion()

        if (version == null && it.deletedAt == null) {
            testplay {
                mapInfo = it
                refreshPage = reloadPage
                this.history = history
                updateMapinfo = setMapCb
            }
        } else {
            modal {
                callbacks = modalRef
            }

            modalContext.Provider {
                value = modalRef

                mapInfo {
                    mapInfo = it
                    reloadMap = loadMap
                    updateMapinfo = setMapCb
                }
                div {
                    className = ClassName("row mt-3")
                    div {
                        className = ClassName("col-lg-4 text-nowrap")
                        infoTable {
                            this.map = it
                            selected = selectedDiff
                            changeSelectedDiff = changeDiff
                        }
                    }

                    div {
                        className = ClassName("col-lg-8")
                        mapPageNav {
                            this.map = it
                            this.tab = tab
                            this.setTab = { newTab ->
                                setTab(newTab)
                            }
                        }

                        Suspense {
                            fallback = loadingElem
                            playlists.table {
                                mapId = it.id
                                visible = tab == MapTabs.Playlists
                                small = true
                            }
                        }

                        reviewTable {
                            this.map = it
                            mapUploaderId = it.uploader.id
                            collaborators = it.collaborators
                            visible = tab == MapTabs.Reviews
                        }

                        if ((tab == MapTabs.ScoreSaber || tab == MapTabs.BeatLeader) && version != null && it.deletedAt == null) {
                            scoreTable {
                                mapKey = version.hash
                                selected = selectedDiff
                                type = if (tab == MapTabs.BeatLeader) LeaderboardType.BeatLeader else LeaderboardType.ScoreSaber
                            }
                        }
                    }
                }
            }
        }
    }
}
