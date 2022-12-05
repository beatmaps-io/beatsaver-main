package io.beatmaps.maps

import external.reactFor
import io.beatmaps.api.LeaderboardType
import io.beatmaps.api.MapDetail
import io.beatmaps.api.ReviewConstants
import kotlinx.html.InputType
import kotlinx.html.id
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import react.Props
import react.dom.div
import react.dom.input
import react.dom.label
import react.functionComponent

external interface MapPageNavProps : Props {
    var map: MapDetail
    var comments: Boolean?
    var setComments: (() -> Unit)?
    var type: LeaderboardType?
    var setType: ((LeaderboardType) -> Unit)?
}

val mapPageNav = functionComponent<MapPageNavProps> {
    val mapAttributes = listOfNotNull(
        if (it.map.ranked) "ranked" else null,
        if (it.map.qualified && !it.map.ranked) "qualified" else null,
        if (it.map.curator != null) "curated" else null,
        if (!it.map.ranked && !it.map.qualified && it.map.curator == null && it.map.uploader.verifiedMapper) "verified" else null
    )

    val classes = mapAttributes.plus("list-group mb-3").joinToString(" ")

    div(classes) {
        div("color") {
            attrs.title = mapAttributes.joinToString(" + ")
        }
        div("btn-group nav-btn-group") {
            val ssChecked = it.comments != true && it.type != LeaderboardType.BeatLeader
            val blChecked = it.comments != true && it.type == LeaderboardType.BeatLeader
            val rvChecked = it.comments == true

            input(InputType.radio, name = "nav", classes = "btn-check") {
                attrs.id = "nav-ss"
            }
            label("btn btn-" + if (ssChecked) "primary" else "secondary") {
                attrs.onClickFunction = { e ->
                    e.preventDefault()
                    it.setType?.invoke(LeaderboardType.ScoreSaber)
                }
                attrs.reactFor = "nav-ss"
                +"ScoreSaber"
            }

            input(InputType.radio, name = "nav", classes = "btn-check") {
                attrs.id = "nav-bl"
            }
            label("btn btn-" + if (blChecked) "primary" else "secondary") {
                attrs.onClickFunction = { e ->
                    e.preventDefault()
                    it.setType?.invoke(LeaderboardType.BeatLeader)
                }
                attrs.reactFor = "nav-bl"
                +"BeatLeader"
            }

            if (ReviewConstants.COMMENTS_ENABLED) {
                input(InputType.radio, name = "nav", classes = "btn-check") {
                    attrs.id = "nav-rv"
                }
                label("btn btn-" + if (rvChecked) "primary" else "secondary") {
                    attrs.onClickFunction = { e ->
                        e.preventDefault()
                        it.setComments?.invoke()
                    }
                    attrs.reactFor = "nav-rv"
                    +"Reviews"
                }
            }
        }
    }
}
