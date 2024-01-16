package io.beatmaps

import history.Location
import io.beatmaps.common.json
import io.beatmaps.nav.manageNav
import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.js.jso
import kotlinx.js.timers.setTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HashChangeEvent
import org.w3c.dom.asList
import org.w3c.dom.get
import org.w3c.dom.set
import react.Component
import react.Props
import react.RBuilder
import react.createElement
import react.fc
import react.react
import react.router.NavigateFunction
import react.router.NavigateOptions
import react.router.Params
import react.router.Route
import react.router.useLocation
import react.router.useNavigate
import react.router.useParams
import kotlin.reflect.KClass

external interface WithRouterProps : Props {
    var history: History
    var location: Location
    var params: Params
}

inline fun <reified T> stateNavOptions(obj: T, replace: Boolean? = null) = object : NavigateOptions {
    override var replace = replace
    override var state: Any? = json.encodeToString(obj)
}

abstract class NO(override var replace: Boolean?) : NavigateOptions {
    override var state: Any? = null
}

object ReplaceNavOption : NO(true)
object NewNavOption : NO(false)

inline fun <reified T> Location.readState() = (state as? String)?.let { json.decodeFromString<T>(it) }

class History(private val navigation: NavigateFunction) {
    fun push(location: String) = go(location, NewNavOption)
    fun replace(location: String) = go(location, ReplaceNavOption)

    fun go(location: String, nav: NavigateOptions) {
        navigation.invoke(location, nav)
    }
}

fun <P : WithRouterProps> RBuilder.withRouter(klazz: KClass<out Component<P, *>>, block: P.() -> Unit) =
    child(
        klazz.react,
        jso {
            location = useLocation()
            history = History(useNavigate())
            params = useParams()
            block(this)
        }
    )

fun <P : WithRouterProps> RBuilder.bsroute(
    path: String,
    klazz: KClass<out Component<P, *>>,
    handler: (P.() -> Unit)? = null
) {
    bsroute(path) {
        withRouter(klazz) {
            handler?.invoke(this)
        }
    }
}

fun <P : Props> RBuilder.bsroute(
    path: String,
    klazz: KClass<out Component<P, *>>,
    handler: (P.() -> Unit)? = null
) {
    bsroute(path) {
        child(
            klazz.react,
            jso {
                handler?.invoke(this)
            }
        )
    }
}

fun RBuilder.bsroute(
    path: String,
    replaceHomelink: Boolean = true,
    render: RBuilder.() -> Unit
) {
    Route {
        attrs.path = path

        // Create a dummy functional component that manages fixing the headers
        attrs.element = createElement(
            fc {
                initWithHistory(History(useNavigate()), replaceHomelink)
                render()
            }
        )
    }
}

// Page setup
var init = false

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
    if (init) return

    (document.getElementById("root") as? HTMLElement)?.addEventListener("click", { e ->
        (e.target as HTMLElement).closest("a")?.let { link ->
            if (link.getAttribute("data-bs") == "local") {
                e.preventDefault()
                history.push(link.getAttribute("href") ?: "")
            }
        }
    })

    (document.getElementById("site-notice") as? HTMLElement)?.let { banner ->
        if (localStorage["banner"] != "4") {
            banner.style.display = "block"

            val closeButton = banner.getElementsByTagName("button")[0]
            closeButton?.addEventListener("click", {
                banner.style.opacity = "0"
                localStorage["banner"] = "4"

                setTimeout({
                    banner.style.display = "none"
                }, 400)
            })
        }
    }

    if (replaceHomelink) {
        fixLink("home-link", history) {
            window.dispatchEvent(HashChangeEvent("hashchange"))
        }
        document.getElementsByClassName("auto-router")
            .asList()
            .filterIsInstance<HTMLAnchorElement>()
            .forEach {
                fixLink(element = it, history = history)
            }
    }

    manageNav()
    init = true
}
