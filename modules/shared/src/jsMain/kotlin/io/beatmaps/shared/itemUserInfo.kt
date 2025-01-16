package io.beatmaps.shared

import external.TimeAgo
import external.routeLink
import io.beatmaps.api.UserDetail
import io.beatmaps.user.ProfileTab
import kotlinx.browser.window
import kotlinx.datetime.Instant
import react.PropsWithChildren
import react.fc
import web.window.WindowTarget

external interface ItemUserInfo : PropsWithChildren {
    var users: List<UserDetail>?
    var tab: ProfileTab?
    var time: Instant?
}

fun UserDetail.profileLink(tab: ProfileTab?, absolute: Boolean = false) = profileLink(tab?.tabText?.lowercase(), absolute)

val itemUserInfo = fc<ItemUserInfo>("playlistOwner") { props ->
    val target = if (window.top === window.self) null else WindowTarget._top
    val users = props.users
    users?.forEachIndexed { idx, u ->
        routeLink(u.profileLink(props.tab), target = target) {
            +u.name
        }
        if (idx < users.lastIndex) +", "
    }
    props.children()
    props.time?.let { time ->
        +" - "
        TimeAgo.default {
            attrs.date = time.toString()
        }
    }
}
