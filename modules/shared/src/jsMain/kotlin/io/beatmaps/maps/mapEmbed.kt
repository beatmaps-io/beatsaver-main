package io.beatmaps.maps

import external.axiosGet
import io.beatmaps.Config
import io.beatmaps.api.MapDetail
import io.beatmaps.index.beatmapInfo
import io.beatmaps.util.fcmemo
import io.beatmaps.util.useAudio
import react.Props
import react.dom.html.ReactHTML.div
import react.router.useParams
import react.useEffect
import react.useState
import web.cssom.ClassName

val mapEmbed = fcmemo<Props>("mapEmbed") {
    val (map, setMap) = useState<MapDetail?>(null)
    val (missing, setMissing) = useState(false)

    val params = useParams()
    val audio = useAudio()

    fun loadMap() {
        val mapKey = params["mapKey"]

        setMissing(false)
        setMap(null)

        axiosGet<MapDetail>(
            "${Config.apibase}/maps/id/$mapKey"
        ).then {
            setMap(it.data)
        }.catch { setMissing(true) }
    }

    useEffect(params["mapKey"]) {
        loadMap()
    }

    div {
        attrs.className = ClassName("embed")
        if (map != null) {
            beatmapInfo {
                attrs.obj = map
                attrs.version = map.mainVersion()
                attrs.audio = audio
            }
        } else {
            div {
                attrs.className = ClassName("card missing")
                if (missing) {
                    +"Missing beatmap"
                } else {
                    +"Loading beatmap..."
                }
            }
        }
    }
}
