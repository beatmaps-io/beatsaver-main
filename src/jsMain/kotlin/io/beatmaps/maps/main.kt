package io.beatmaps.maps

import external.axiosGet
import io.beatmaps.Config
import io.beatmaps.History
import io.beatmaps.api.LeaderboardType
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapDifficulty
import io.beatmaps.index.ModalCallbacks
import io.beatmaps.index.modal
import io.beatmaps.index.modalContext
import io.beatmaps.maps.testplay.testplay
import io.beatmaps.playlist.playlistTable
import io.beatmaps.setPageTitle
import io.beatmaps.shared.review.reviewTable
import io.beatmaps.util.useDidUpdateEffect
import kotlinx.browser.localStorage
import kotlinx.browser.window
import org.w3c.dom.get
import org.w3c.dom.set
import react.Props
import react.dom.div
import react.fc
import react.router.useNavigate
import react.router.useParams
import react.useEffect
import react.useEffectOnce
import react.useRef
import react.useState

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

val mapPage = fc<MapPageProps> { props ->
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

    fun loadMap() {
        val mapKey = params["mapKey"]
        val subPath = if (props.beatsaver) "beatsaver" else "id"

        setMap(null)
        setSelectedDiff(null)
        setTab(localStorageTab)

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
                attrs.callbacks = modalRef
            }

            modalContext.Provider {
                attrs.value = modalRef

                mapInfo {
                    attrs {
                        mapInfo = it
                        reloadMap = ::loadMap
                        deleteMap = { self ->
                            history.push("/profile" + if (!self) "/${it.uploader.id}" else "")
                        }
                        updateMapinfo = { map ->
                            setMap(map)
                        }
                    }
                }
                div("row mt-3") {
                    div("col-lg-4 text-nowrap") {
                        infoTable {
                            attrs.map = it
                            attrs.selected = selectedDiff
                            attrs.changeSelectedDiff = { diff ->
                                setSelectedDiff(diff)
                            }
                        }
                    }

                    div("col-lg-8") {
                        mapPageNav {
                            attrs.map = it
                            attrs.tab = tab
                            attrs.setTab = {
                                setTab(it)
                            }
                        }

                        playlistTable {
                            attrs.mapId = it.id
                            attrs.visible = tab == MapTabs.Playlists
                            attrs.small = true
                        }

                        reviewTable {
                            attrs.map = it
                            attrs.mapUploaderId = it.uploader.id
                            attrs.collaborators = it.collaborators
                            attrs.visible = tab == MapTabs.Reviews
                        }

                        if ((tab == MapTabs.ScoreSaber || tab == MapTabs.BeatLeader) && version != null && it.deletedAt == null) {
                            scoreTable {
                                attrs.mapKey = version.hash
                                attrs.selected = selectedDiff
                                attrs.type = if (tab == MapTabs.BeatLeader) LeaderboardType.BeatLeader else LeaderboardType.ScoreSaber
                            }
                        }
                    }
                }
            }
        }
    }
}
