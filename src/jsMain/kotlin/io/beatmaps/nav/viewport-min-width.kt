package io.beatmaps.nav

import kotlinx.browser.document
import kotlinx.browser.window

fun viewportMinWidthPolyfill() {
    document.querySelector("meta[name=viewport]")?.let { viewport ->
        val content = viewport.getAttribute("content") ?: ""
        val minWidthPart = content.split(",").map { it.trim().split("=") }.firstOrNull { it[0] === "min-width" }
        minWidthPart?.get(1)?.toInt()?.let { minWidth ->
            fun updateViewportSize() {
                if (window.screen.width < minWidth) {
                    document.head?.removeChild(viewport)

                    val newViewport = document.createElement("meta")
                    newViewport.setAttribute("name", "viewport")
                    newViewport.setAttribute("content", "width=$minWidth")
                    document.head?.appendChild(newViewport)
                }
            }
            window.onresize = { updateViewportSize() }
            updateViewportSize()
        }
    }
}