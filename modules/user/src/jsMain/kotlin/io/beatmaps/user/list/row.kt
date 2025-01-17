package io.beatmaps.user.list

import external.Moment
import external.routeLink
import io.beatmaps.Config
import io.beatmaps.Config.dateFormat
import io.beatmaps.api.UserDetail
import io.beatmaps.common.fixed
import io.beatmaps.common.formatTime
import io.beatmaps.util.fcmemo
import kotlinx.datetime.Clock
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.tr
import web.cssom.ClassName
import web.window.WindowTarget

external interface UserListRowProps : Props {
    var idx: Int?
    var user: UserDetail?
}

val userListRow = fcmemo<UserListRowProps>("UserListRow") { props ->
    tr {
        td {
            +"${props.idx}"
        }
        props.user?.let { u ->
            td {
                img {
                    attrs.alt = "${u.name} avatar"
                    attrs.src = u.avatar
                    attrs.className = ClassName("rounded-circle")
                    attrs.width = 40.0
                    attrs.height = 40.0
                }
            }
            td {
                routeLink(u.profileLink()) {
                    +u.name
                }
            }
            u.stats?.let { stats ->
                td {
                    +"${stats.avgBpm}"
                }
                td {
                    +stats.avgDuration.formatTime()
                }
                td {
                    +stats.totalUpvotes.toLocaleString()
                }
                td {
                    +stats.totalDownvotes.toLocaleString()
                }
                td {
                    val total = ((stats.totalUpvotes + stats.totalDownvotes + 0.001f) * 0.01f)
                    +"${(stats.totalUpvotes / total).fixed(2)}%"
                }
                td {
                    +stats.totalMaps.toLocaleString()
                }
                td {
                    +stats.rankedMaps.toLocaleString()
                }
                td {
                    +Moment(stats.firstUpload.toString()).format(dateFormat)
                }
                td {
                    +Moment(stats.lastUpload.toString()).format(dateFormat)
                }
                td {
                    stats.lastUpload?.let {
                        +(Clock.System.now() - it).inWholeDays.toInt().toLocaleString()
                    }
                }
                td {
                    val last = stats.lastUpload
                    val first = stats.firstUpload
                    if (last != null && first != null) {
                        +(last - first).inWholeDays.toInt().toLocaleString()
                    }
                }
            } ?: run {
                repeat(11) { td { } }
            }
            td {
                a {
                    attrs.href = "${Config.apibase}/users/id/${u.id}/playlist"
                    attrs.target = WindowTarget._blank
                    attrs.className = ClassName("btn btn-secondary")
                    attrs.download = ""
                    i {
                        attrs.className = ClassName("fas fa-list")
                    }
                }
            }
        } ?: run {
            repeat(14) { td { } }
        }
    }
}
