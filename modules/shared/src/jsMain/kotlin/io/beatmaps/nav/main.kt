package io.beatmaps.nav

import js.array.asList
import web.dom.document
import web.events.Event
import web.events.EventHandler
import web.html.HTMLButtonElement
import web.html.HTMLDivElement
import web.html.HTMLElement
import web.timers.setTimeout

data class DropDown(val menu: HTMLElement, val button: HTMLElement, var shouldClose: Boolean = false)

fun manageNav() {
    val navButton = document.getElementById("navbar-button") as? HTMLButtonElement
    val navMenu = document.getElementById("navbar") as? HTMLDivElement

    if (navMenu == null || navButton == null) return

    navButton.onclick = EventHandler { it: Event ->
        it.preventDefault()
        val expanding = !navMenu.classList.contains("show")

        if (expanding) {
            navMenu.classList.add("collapsing")
        }

        val oldHeight = if (expanding) 0 else navMenu.scrollHeight

        // Set old height -> change classes -> set new height -> change classes

        navMenu.style.height = "${oldHeight}px"
        setTimeout(
            {
                navMenu.classList.add("collapsing")
                navMenu.classList.remove("collapse", "show")

                val newHeight = if (expanding) navMenu.scrollHeight else 0
                navMenu.style.height = "${newHeight}px"

                setTimeout(
                    {
                        navMenu.classList.add("collapse")
                        navMenu.classList.remove("collapsing")
                        navMenu.style.height = ""
                        if (expanding) {
                            navMenu.classList.add("show")
                        }
                    },
                    500
                )
            },
            1
        )
    }

    val dropdowns = document.getElementsByClassName("dropdown")
    val dropDownInfo = dropdowns.asList().map {
        val childList = it.children.asList()
        val dropdown = DropDown(
            childList.find { child -> child.classList.contains("dropdown-menu") } as HTMLElement,
            childList.find { child -> child.classList.contains("dropdown-toggle") } as HTMLElement
        )

        dropdown.button.onblur = EventHandler {
            dropdown.shouldClose = dropdown.menu.classList.contains("show")
        }

        dropdown.button.onclick = EventHandler { e ->
            e.preventDefault()
            if (dropdown.menu.classList.contains("show")) {
                dropdown.menu.classList.remove("show")
                dropdown.button.classList.remove("show")
            } else {
                dropdown.menu.classList.add("show")
                dropdown.button.classList.add("show")
            }
        }

        dropdown
    }

    document.onmouseup = EventHandler {
        dropDownInfo.forEach {
            if (it.shouldClose) {
                it.menu.classList.remove("show")
                it.button.classList.remove("show")
            }
        }
    }
}
