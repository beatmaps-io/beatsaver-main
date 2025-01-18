package io.beatmaps.nav

import web.dom.document
import web.events.Event
import web.events.addEventListener
import web.screen.screen
import web.window.window

fun viewportMinWidthPolyfill() {
    document.querySelector("meta[name=viewport]")?.let { viewport ->
        val content = viewport.getAttribute("content") ?: ""
        val minWidthPart = content.split(",").map { it.trim().split("=") }.firstOrNull { it[0] === "min-width" }
        var currentState = false
        minWidthPart?.get(1)?.toInt()?.let { minWidth ->
            val updateViewportSize = { _: Event? ->
                if (screen.width < minWidth != currentState) {
                    currentState = screen.width < minWidth
                    document.querySelector("meta[name=viewport]")?.let { currentViewport ->
                        document.head.removeChild(currentViewport)
                    }

                    if (screen.width < minWidth) {
                        val newViewport = document.createElement("meta")
                        newViewport.setAttribute("name", "viewport")
                        newViewport.setAttribute("content", "width=$minWidth")
                        document.head.appendChild(newViewport)
                    } else {
                        document.head.appendChild(viewport)
                    }
                }
            }
            window.addEventListener(Event.RESIZE, updateViewportSize)
            updateViewportSize(null)
        }
    }
}
