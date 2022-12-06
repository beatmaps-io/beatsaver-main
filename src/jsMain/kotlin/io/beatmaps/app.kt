package io.beatmaps

import external.ReactDatesInit
import io.beatmaps.index.HomePage
import io.beatmaps.maps.MapPage
import io.beatmaps.maps.recent.recentTestplays
import io.beatmaps.modlog.ModLog
import io.beatmaps.modreview.ModReview
import io.beatmaps.nav.manageNav
import io.beatmaps.nav.viewportMinWidthPolyfill
import io.beatmaps.playlist.EditPlaylist
import io.beatmaps.playlist.Playlist
import io.beatmaps.playlist.PlaylistFeed
import io.beatmaps.upload.UploadPage
import io.beatmaps.user.PickUsernamePage
import io.beatmaps.user.ProfilePage
import io.beatmaps.user.ResetPage
import io.beatmaps.user.UserList
import io.beatmaps.user.alerts.AlertsPage
import io.beatmaps.user.authorizePage
import io.beatmaps.user.forgotPage
import io.beatmaps.user.loginPage
import io.beatmaps.user.signupPage
import kotlinext.js.jso
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HashChangeEvent
import org.w3c.dom.asList
import react.Component
import react.Props
import react.RBuilder
import react.RComponent
import react.State
import react.createContext
import react.dom.div
import react.dom.render
import react.fc
import react.router.dom.BrowserRouter
import react.router.dom.History
import react.router.dom.Route
import react.router.dom.Switch
import react.router.dom.useHistory
import react.router.dom.withRouter
import kotlin.reflect.KClass

fun setPageTitle(page: String) {
    document.title = "BeatSaver - $page"
}

external interface UserData {
    val userId: Int
    val admin: Boolean
    val curator: Boolean
    val suspended: Boolean
}

val globalContext = createContext<UserData?>(null)

fun main() {
    ReactDatesInit // This actually needs to be referenced I guess
    window.onload = {
        val root = document.getElementById("root")
        render(root) {
            globalContext.Provider {
                attrs.value = document.getElementById("user-data")?.let {
                    JSON.parse<UserData>(it.textContent ?: "")
                }
                child(App::class) { }
            }
        }
    }
}

const val dateFormat = "YYYY-MM-DD"

class App : RComponent<Props, State>() {
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

    override fun componentDidMount() {
        viewportMinWidthPolyfill()
    }

    fun <P : Props> RBuilder.bsroute(
        vararg path: String,
        klazz: KClass<out Component<P, *>>,
        handler: (P.() -> Unit)? = null
    ) {
        bsroute(*path, exact = true) {
            child(
                withRouter(klazz),
                jso {
                    handler?.invoke(this)
                }
            )
        }
    }

    fun RBuilder.bsroute(
        vararg path: String,
        exact: Boolean = false,
        strict: Boolean = false,
        replaceHomelink: Boolean = true,
        render: RBuilder.() -> Unit
    ) {
        Route {
            attrs.exact = exact
            attrs.strict = strict
            attrs.path = path

            // Create a dummy functional component that manages fixing the headers
            child(
                fc {
                    initWithHistory(useHistory(), replaceHomelink)
                    render()
                }
            )
        }
    }

    override fun RBuilder.render() {
        BrowserRouter {
            Switch {
                bsroute("/", klazz = HomePage::class)
                bsroute("/beatsaver/:mapKey", klazz = MapPage::class) {
                    beatsaver = true
                }
                bsroute("/maps/:mapKey", klazz = MapPage::class) {
                    beatsaver = false
                }
                bsroute("/upload", klazz = UploadPage::class)
                bsroute("/profile/:userId?", exact = true) {
                    globalContext.Consumer { user ->
                        child(
                            withRouter(ProfilePage::class),
                            jso {
                                userData = user
                            }
                        )
                    }
                }
                bsroute("/alerts", klazz = AlertsPage::class)
                bsroute("/playlists", klazz = PlaylistFeed::class)
                bsroute("/playlists/new", klazz = EditPlaylist::class)
                bsroute("/playlists/:id", klazz = Playlist::class)
                bsroute("/playlists/:id/edit", klazz = EditPlaylist::class)
                bsroute("/test", exact = true) {
                    recentTestplays { }
                }
                bsroute("/modlog", exact = true) {
                    globalContext.Consumer { user ->
                        child(
                            withRouter(ModLog::class),
                            jso {
                                userData = user
                            }
                        )
                    }
                }
                bsroute("/modreview", exact = true) {
                    globalContext.Consumer { user ->
                        child(
                            withRouter(ModReview::class),
                            jso {
                                userData = user
                            }
                        )
                    }
                }
                bsroute("/policy/dmca", exact = true, replaceHomelink = false) {
                    div {}
                }
                bsroute("/policy/tos", exact = true, replaceHomelink = false) {
                    div {}
                }
                bsroute("/policy/privacy", exact = true, replaceHomelink = false) {
                    div {}
                }
                bsroute("/mappers", klazz = UserList::class)
                bsroute("/login", exact = true) {
                    loginPage { }
                }
                bsroute("/oauth2/authorize", exact = true) {
                    authorizePage { }
                }
                bsroute("/register", exact = true) {
                    signupPage { }
                }
                bsroute("/forgot", exact = true) {
                    forgotPage { }
                }
                bsroute("/reset/:jwt", klazz = ResetPage::class)
                bsroute("/username", klazz = PickUsernamePage::class)
                bsroute("*") {
                    notFound { }
                }
            }
        }
    }
}
