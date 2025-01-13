package io.beatmaps

import io.beatmaps.common.json
import io.beatmaps.nav.manageNav
import io.beatmaps.playlist.loading
import js.objects.jso
import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.dom.removeClass
import kotlinx.serialization.encodeToString
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HashChangeEvent
import org.w3c.dom.asList
import org.w3c.dom.get
import org.w3c.dom.set
import react.Props
import react.RBuilder
import react.Suspense
import react.createElement
import react.fc
import react.router.NavigateFunction
import react.router.NavigateOptions
import react.router.RouteObject
import react.router.useNavigate
import remix.run.router.Location
import remix.run.router.Params
import web.timers.setTimeout

external interface WithRouterProps : Props {
    var history: History
    var location: Location<*>
    var params: Params
}

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
    render: RBuilder.() -> Unit
) = jso<RouteObject> {
    this.path = path
    element = createElement(
        fc {
            initWithHistory(History(useNavigate()), replaceHomelink)
            Suspense {
                attrs.fallback = createElement(loading)
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
            elem.onclick = {
                history.push(href)
                block(elem)
                it.preventDefault()
            }
        }
    }
}

private fun initWithHistory(history: History, replaceHomelink: Boolean = true) {
    if (historyInitState) return

    val navMenu = document.getElementById("navbar") as? HTMLDivElement
    val hideMenu: () -> Unit = {
        navMenu?.removeClass("collapsing", "show")
    }

    (document.getElementById("root") as? HTMLElement)?.addEventListener("click", { e ->
        (e.target as HTMLElement).closest("a")?.let { link ->
            if (link.getAttribute("data-bs") == "local") {
                e.preventDefault()
                history.push(link.getAttribute("href") ?: "")
            }
        }
    })

    (document.getElementById("site-notice") as? HTMLElement)?.let { banner ->
        val id = banner.attributes["data-id"]?.value ?: ""
        if (localStorage["banner"] != id) {
            banner.style.display = "block"

            val closeButton = banner.getElementsByTagName("button")[0]
            closeButton?.addEventListener("click", {
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
            window.dispatchEvent(HashChangeEvent("hashchange"))
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
