package io.beatmaps.util

import io.beatmaps.api.UserAlertStats
import kotlinx.browser.document

fun updateAlertDisplay(stats: UserAlertStats) {
    document.getElementById("alert-count")?.apply {
        stats.unread.let { count ->
            setAttribute("data-count", count.toString())
            innerHTML = if (count < 10) count.toString() else "9+"
        }
    }
}
