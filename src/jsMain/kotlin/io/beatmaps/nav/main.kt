package io.beatmaps.nav

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.dom.addClass
import kotlinx.dom.hasClass
import kotlinx.dom.removeClass
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList

data class DropDown(val menu: HTMLElement, val button: HTMLElement, var shouldClose: Boolean = false)

fun manageNav() {
    val navButton = document.getElementById("navbar-button") as HTMLButtonElement
    val navMenu = document.getElementById("navbar") as HTMLDivElement
    navButton.onclick = {
        it.preventDefault()
        val expanding = !navMenu.hasClass("show")

        if (expanding) {
            navMenu.addClass("collapsing")
        }

        val oldHeight = if (expanding) 0 else navMenu.scrollHeight

        // Set old height -> change classes -> set new height -> change classes

        navMenu.style.height = "${oldHeight}px"
        window.setTimeout(
            {
                navMenu.addClass("collapsing")
                navMenu.removeClass("collapse", "show")

                val newHeight = if (expanding) navMenu.scrollHeight else 0
                navMenu.style.height = "${newHeight}px"

                window.setTimeout(
                    {
                        navMenu.addClass("collapse")
                        navMenu.removeClass("collapsing")
                        navMenu.style.height = ""
                        if (expanding) {
                            navMenu.addClass("show")
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
            childList.find { child -> child.hasClass("dropdown-menu") } as HTMLElement,
            childList.find { child -> child.hasClass("dropdown-toggle") } as HTMLElement
        )

        dropdown.button.onblur = {
            dropdown.shouldClose = dropdown.menu.hasClass("show")
            true
        }

        dropdown.button.onclick = { e ->
            e.preventDefault()
            if (dropdown.menu.hasClass("show")) {
                dropdown.menu.removeClass("show")
                dropdown.button.removeClass("show")
            } else {
                dropdown.menu.addClass("show")
                dropdown.button.addClass("show")
            }
        }

        dropdown
    }

    document.onmouseup = {
        dropDownInfo.forEach {
            if (it.shouldClose) {
                it.menu.removeClass("show")
                it.button.removeClass("show")
            }
        }
    }
}
