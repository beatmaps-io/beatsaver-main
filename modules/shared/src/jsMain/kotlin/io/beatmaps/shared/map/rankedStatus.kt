package io.beatmaps.shared.map

import io.beatmaps.api.MapDetail
import io.beatmaps.util.fcmemo
import react.Props
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.span
import web.cssom.ClassName

external interface RankedStatusProps : Props {
    var map: MapDetail
}

val rankedStatus = fcmemo<RankedStatusProps>("rankedStatus") { props ->
    val criteria = with(props.map) {
        listOf(
            // Name, ranked, qualified
            Triple("ScoreSaber", ranked, qualified),
            Triple("BeatLeader", blRanked, blQualified)
        )
    }

    criteria.forEach {
        if (it.second || it.third) {
            span {
                className = ClassName("ranked-status ${it.first.lowercase()}")
                img {
                    alt = it.first
                    src = "/static/${it.first.lowercase()}.svg"
                    width = 16.0
                    height = 16.0
                }
                +(if (it.second) "Ranked" else "Qualified")
            }
        }
    }
}
