package io.beatmaps

import external.ReactDatesInit
import io.beatmaps.common.api.EAlertType
import io.beatmaps.index.HomePage
import io.beatmaps.maps.MapPage
import io.beatmaps.maps.MapPageProps
import io.beatmaps.maps.recent.recentTestplays
import io.beatmaps.modlog.modlog
import io.beatmaps.nav.manageNav
import io.beatmaps.nav.viewportMinWidthPolyfill
import io.beatmaps.playlist.PlaylistProps
import io.beatmaps.playlist.editPlaylist
import io.beatmaps.playlist.playlist
import io.beatmaps.playlist.playlistFeed
import io.beatmaps.upload.UploadPage
import io.beatmaps.user.ProfilePage
import io.beatmaps.user.ProfilePageProps
import io.beatmaps.user.ResetPageProps
import io.beatmaps.user.alerts.AlertsPage
import io.beatmaps.user.authorizePage
import io.beatmaps.user.forgotPage
import io.beatmaps.user.loginPage
import io.beatmaps.user.pickUsernamePage
import io.beatmaps.user.resetPage
import io.beatmaps.user.signupPage
import io.beatmaps.user.userList
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HashChangeEvent
import org.w3c.dom.asList
import org.w3c.dom.url.URLSearchParams
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.createContext
import react.dom.div
import react.dom.render
import react.router.dom.RouteResultHistory
import react.router.dom.RouteResultProps
import react.router.dom.browserRouter
import react.router.dom.route
import react.router.dom.switch
import react.setState

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

external interface AppState : RState {
    var init: Boolean?
}

class App : RComponent<RProps, AppState>() {
    private fun fixLink(id: String = "", history: RouteResultHistory, element: HTMLAnchorElement? = null, block: (HTMLAnchorElement) -> Unit = {}) {
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

    private fun initWithHistory(history: RouteResultHistory, replaceHomelink: Boolean = true) {
        if (state.init == true) return

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

        setState {
            init = true
        }
    }

    override fun componentWillMount() {
        viewportMinWidthPolyfill()
    }

    fun <T : RProps> RBuilder.bsroute(
        path: String,
        exact: Boolean = false,
        strict: Boolean = false,
        replaceHomelink: Boolean = true,
        render: RBuilder.(RouteResultProps<T>) -> Unit
    ) {
        route<T>(path, exact = exact, strict = strict) {
            initWithHistory(it.history, replaceHomelink)
            render(it)
        }
    }

    override fun RBuilder.render() {
        browserRouter {
            switch {
                bsroute<RProps>("/", exact = true) {
                    child(HomePage::class) {
                        attrs.history = it.history
                    }
                }
                bsroute<MapPageProps>("/beatsaver/:mapKey", exact = true) {
                    child(MapPage::class) {
                        attrs.history = it.history
                        attrs.mapKey = it.match.params.mapKey
                        attrs.beatsaver = true
                    }
                }
                bsroute<MapPageProps>("/maps/:mapKey", exact = true) {
                    child(MapPage::class) {
                        attrs.history = it.history
                        attrs.mapKey = it.match.params.mapKey
                        attrs.beatsaver = false
                    }
                }
                bsroute<RProps>("/upload", exact = true) {
                    child(UploadPage::class) {
                        attrs.history = it.history
                    }
                }
                bsroute<ProfilePageProps>("/profile/:userId?", exact = true) {
                    globalContext.Consumer { userData ->
                        child(ProfilePage::class) {
                            key = "profile-${it.match.params.userId}"
                            attrs.history = it.history
                            attrs.userData = userData
                            attrs.userId = it.match.params.userId
                        }
                    }
                }
                bsroute<RProps>("/alerts", exact = true) {
                    child(AlertsPage::class) {
                        attrs.history = it.history
                        URLSearchParams(window.location.search).let { u ->
                            attrs.read = u.get("read")?.toBoolean()
                            attrs.filters = u.get("type")?.split(",")?.mapNotNull { EAlertType.fromLower(it) }
                        }
                    }
                }
                bsroute<RProps>("/playlists", exact = true) {
                    playlistFeed {
                        history = it.history
                    }
                }
                bsroute<RProps>("/playlists/new", exact = true) {
                    editPlaylist {
                        id = null
                        history = it.history
                    }
                }
                bsroute<PlaylistProps>("/playlists/:id", exact = true) {
                    playlist {
                        id = it.match.params.id
                        history = it.history
                    }
                }
                bsroute<PlaylistProps>("/playlists/:id/edit", exact = true) {
                    editPlaylist {
                        id = it.match.params.id
                        history = it.history
                    }
                }
                bsroute<RProps>("/test", exact = true) {
                    recentTestplays { }
                }
                bsroute<RProps>("/modlog", exact = true) {
                    globalContext.Consumer { user ->
                        modlog {
                            history = it.history
                            userData = user
                            URLSearchParams(window.location.search).let { u ->
                                mod = u.get("mod") ?: ""
                                this.user = u.get("user") ?: ""
                            }
                        }
                    }
                }
                bsroute<RProps>("/policy/dmca", exact = true, replaceHomelink = false) {
                    div {}
                }
                bsroute<RProps>("/policy/tos", exact = true, replaceHomelink = false) {
                    div {}
                }
                bsroute<RProps>("/policy/privacy", exact = true, replaceHomelink = false) {
                    div {}
                }
                bsroute<RProps>("/mappers", exact = true) {
                    userList {
                        history = it.history
                    }
                }
                bsroute<RProps>("/login", exact = true) {
                    loginPage { }
                }
                bsroute<RProps>("/oauth2/authorize", exact = true) {
                    authorizePage { }
                }
                bsroute<RProps>("/register", exact = true) {
                    signupPage { }
                }
                bsroute<RProps>("/forgot", exact = true) {
                    forgotPage { }
                }
                bsroute<ResetPageProps>("/reset/:jwt", exact = true) {
                    resetPage {
                        jwt = it.match.params.jwt
                        history = it.history
                    }
                }
                bsroute<RProps>("/username", exact = true) {
                    pickUsernamePage {
                        history = it.history
                    }
                }
                bsroute<RProps>("*") {
                    notFound { }
                }
            }
        }
    }
}
