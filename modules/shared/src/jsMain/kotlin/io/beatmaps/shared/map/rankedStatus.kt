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
                attrs.className = ClassName("ranked-status ${it.first.lowercase()}")
                img {
                    attrs.alt = it.first
                    attrs.src = "/static/${it.first.lowercase()}.svg"
                    attrs.width = 16.0
                    attrs.height = 16.0
                }
                +(if (it.second) "Ranked" else "Qualified")
            }
        }
    }
}
