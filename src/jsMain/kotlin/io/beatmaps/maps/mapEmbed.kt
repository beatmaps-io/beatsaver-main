package io.beatmaps.maps

import external.axiosGet
import io.beatmaps.Config
import io.beatmaps.History
import io.beatmaps.api.MapDetail
import io.beatmaps.index.beatmapInfo
import io.beatmaps.util.useAudio
import react.Props
import react.dom.div
import react.fc
import react.router.useNavigate
import react.router.useParams
import react.useEffect
import react.useState

val mapEmbed = fc<Props> {
    val (map, setMap) = useState<MapDetail?>(null)

    val params = useParams()
    val history = History(useNavigate())
    val audio = useAudio()

    fun loadMap() {
        val mapKey = params["mapKey"]

        setMap(null)

        axiosGet<MapDetail>(
            "${Config.apibase}/maps/id/$mapKey"
        ).then {
            setMap(it.data)
        }.catch {
            history.push("/")
        }
    }

    useEffect(params["mapKey"]) {
        loadMap()
    }

    div("embed") {
        beatmapInfo {
            this.obj = map
            this.version = map?.mainVersion()
            this.audio = audio
        }
    }
}
