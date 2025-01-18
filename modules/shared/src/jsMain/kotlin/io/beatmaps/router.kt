package io.beatmaps

import io.beatmaps.common.json
import io.beatmaps.nav.manageNav
import io.beatmaps.shared.loadingElem
import io.beatmaps.util.fcmemo
import io.beatmaps.util.get
import io.beatmaps.util.set
import js.array.asList
import js.objects.jso
import kotlinx.serialization.encodeToString
import react.ChildrenBuilder
import react.Suspense
import react.createElement
import react.router.NavigateFunction
import react.router.NavigateOptions
import react.router.RouteObject
import react.router.useNavigate
import remix.run.router.Location
import web.dom.document
import web.events.Event
import web.events.EventHandler
import web.events.addEventListener
import web.history.HashChangeEvent
import web.html.HTML
import web.html.HTMLAnchorElement
import web.html.HTMLDivElement
import web.html.HTMLElement
import web.storage.localStorage
import web.timers.setTimeout
import web.uievents.MouseEvent
import web.window.window

inline fun <reified T> stateNavOptions(obj: T, r: Boolean? = null) = jso<NavigateOptions> {
    replace = r
    state = json.encodeToString(obj)
}

inline fun <reified T> Location<*>.readState() = (state as? String)?.let { json.decodeFromString<T>(it) }

class History(private val navigation: NavigateFunction) {
    fun push(location: String) = go(location, NewNavOption)
    fun replace(location: String) = go(location, ReplaceNavOption)

    fun go(location: String, nav: NavigateOptions) {
        navigation.invoke(location, nav)
    }

    companion object {
        val ReplaceNavOption = jso<NavigateOptions> {
            replace = true
        }

        val NewNavOption = jso<NavigateOptions> {
            replace = false
        }
    }
}

fun bsroute(
    path: String,
    replaceHomelink: Boolean = true,
    render: ChildrenBuilder.() -> Unit
) = jso<RouteObject> {
    this.path = path
    element = createElement(
        fcmemo("pageWrapper") {
            initWithHistory(History(useNavigate()), replaceHomelink)
            Suspense {
                fallback = loadingElem
                render()
            }
        }
    )
}

// Page setup
private var historyInitState = false

private fun fixLink(id: String = "", history: History, element: HTMLAnchorElement? = null, block: (HTMLAnchorElement) -> Unit = {}) {
    (element ?: document.getElementById(id) as? HTMLAnchorElement)?.let { elem ->
        elem.getAttribute("href")?.let { href ->
            elem.onclick = EventHandler { e: MouseEvent ->
                history.push(href)
                block(elem)
                e.preventDefault()
            }
        }
    }
}

private fun initWithHistory(history: History, replaceHomelink: Boolean = true) {
    if (historyInitState) return

    val navMenu = document.getElementById("navbar") as? HTMLDivElement
    val hideMenu: () -> Unit = {
        navMenu?.classList?.remove("collapsing", "show")
    }

    document.getElementsByTagName(HTML.link).asList().forEach {
        if (!it.dataset["lazy"].isNullOrEmpty()) it.media = "all"
    }

    document.getElementById("root")?.addEventListener(Event.CLICK, { e ->
        (e.target as HTMLElement).closest("a")?.let { link ->
            if (link.getAttribute("data-bs") == "local") {
                e.preventDefault()
                history.push(link.getAttribute("href") ?: "")
            }
        }
    })

    document.getElementById("site-notice")?.let { banner ->
        val id = banner.dataset["id"] ?: ""
        if (localStorage["banner"] != id) {
            banner.style.display = "block"

            val closeButton = banner.getElementsByTagName("button")[0]
            closeButton.addEventListener(Event.CLICK, {
                banner.style.opacity = "0"
                localStorage["banner"] = id

                setTimeout({
                    banner.style.display = "none"
                }, 400)
            })
        }
    }

    if (replaceHomelink) {
        fixLink("home-link", history) {
            hideMenu()
            window.dispatchEvent(HashChangeEvent(HashChangeEvent.HASH_CHANGE))
        }
        document.getElementsByClassName("auto-router")
            .asList()
            .filterIsInstance<HTMLAnchorElement>()
            .forEach {
                fixLink(element = it, history = history) { hideMenu() }
            }
    }

    manageNav()
    historyInitState = true
}
