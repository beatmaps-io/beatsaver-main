package io.beatmaps.nav

import kotlinx.browser.document
import kotlinx.browser.window

fun viewportMinWidthPolyfill() {
    document.querySelector("meta[name=viewport]")?.let { viewport ->
        val content = viewport.getAttribute("content") ?: ""
        val minWidthPart = content.split(",").map { it.trim().split("=") }.firstOrNull { it[0] === "min-width" }
        var currentState = false
        minWidthPart?.get(1)?.toInt()?.let { minWidth ->
            fun updateViewportSize() {
                if (window.screen.width < minWidth == currentState)
                    return

                currentState = window.screen.width < minWidth
                document.querySelector("meta[name=viewport]")?.let { currentViewport ->
                    document.head?.removeChild(currentViewport)
                }

                if (window.screen.width < minWidth) {
                    val newViewport = document.createElement("meta")
                    newViewport.setAttribute("name", "viewport")
                    newViewport.setAttribute("content", "width=$minWidth")
                    document.head?.appendChild(newViewport)
                } else {
                    document.head?.appendChild(viewport)
                }
            }
            window.onresize = { updateViewportSize() }
            updateViewportSize()
        }
    }
}
