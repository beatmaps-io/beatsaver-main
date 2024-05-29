package io.beatmaps.maps

import io.beatmaps.api.LeaderboardType
import io.beatmaps.api.MapDetail
import io.beatmaps.api.ReviewConstants
import kotlinx.html.id
import kotlinx.html.js.onClickFunction
import react.Props
import react.dom.a
import react.dom.li
import react.dom.span
import react.dom.ul
import react.fc

external interface MapPageNavProps : Props {
    var map: MapDetail
    var comments: Boolean?
    var setComments: (() -> Unit)?
    var type: LeaderboardType?
    var setType: ((LeaderboardType) -> Unit)?
}

val mapPageNav = fc<MapPageNavProps> {
    ul("nav nav-minimal mb-3") {
        val ssChecked = it.comments != true && it.type != LeaderboardType.BeatLeader
        val blChecked = it.comments != true && it.type == LeaderboardType.BeatLeader
        val rvChecked = it.comments == true

        li("nav-item") {
            a("#", classes = "nav-link" + if (ssChecked) " active" else "") {
                attrs.id = "nav-ss"
                attrs.onClickFunction = { e ->
                    e.preventDefault()
                    it.setType?.invoke(LeaderboardType.ScoreSaber)
                }
                span {
                    +"ScoreSaber"
                }
            }
        }

        li("nav-item") {
            a("#", classes = "nav-link" + if (blChecked) " active" else "") {
                attrs.id = "nav-bl"
                attrs.onClickFunction = { e ->
                    e.preventDefault()
                    it.setType?.invoke(LeaderboardType.BeatLeader)
                }
                span {
                    +"BeatLeader"
                }
            }
        }

        if (ReviewConstants.COMMENTS_ENABLED) {
            li("nav-item") {
                a("#", classes = "nav-link" + if (rvChecked) " active" else "") {
                    attrs.id = "nav-rv"
                    attrs.onClickFunction = { e ->
                        e.preventDefault()
                        it.setComments?.invoke()
                    }
                    span {
                        +"Reviews"
                    }
                }
            }
        }
    }
}
