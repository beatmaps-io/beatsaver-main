package io.beatmaps.shared.map

import io.beatmaps.api.MapDetail
import io.beatmaps.util.fcmemo
import react.Props
import react.dom.img
import react.dom.span

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
            span("ranked-status ${it.first.lowercase()}") {
                img(it.first, "/static/${it.first.lowercase()}.svg") {
                    attrs.width = "16"
                    attrs.height = "16"
                }
                +(if (it.second) "Ranked" else "Qualified")
            }
        }
    }
}
