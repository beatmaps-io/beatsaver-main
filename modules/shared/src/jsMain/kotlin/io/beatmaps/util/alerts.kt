package io.beatmaps.util

import io.beatmaps.api.UserAlertStats
import web.dom.document

fun updateAlertDisplay(stats: UserAlertStats) {
    document.getElementById("alert-count")?.apply {
        stats.unread.let { count ->
            dataset["count"] = count.toString()
            innerHTML = if (count < 10) count.toString() else "9+"
        }
    }
}
