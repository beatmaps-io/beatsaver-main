package io.beatmaps

import io.beatmaps.index.HomePage
import io.beatmaps.index.HomePageProps
import io.beatmaps.maps.MapPage
import io.beatmaps.maps.MapPageProps
import io.beatmaps.maps.UploadPage
import io.beatmaps.maps.recent.recentTestplays
import io.beatmaps.nav.manageNav
import io.beatmaps.nav.viewportMinWidthPolyfill
import io.beatmaps.playlist.PlaylistProps
import io.beatmaps.playlist.editPlaylist
import io.beatmaps.playlist.playlist
import io.beatmaps.user.ProfilePage
import io.beatmaps.user.ProfilePageProps
import io.beatmaps.user.ResetPageProps
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
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.createContext
import react.dom.div
import react.dom.render
import react.router.dom.RouteResultHistory
import react.router.dom.browserRouter
import react.router.dom.route
import react.router.dom.switch

fun setPageTitle(page: String) {
    document.title = "BeatSaver - $page"
}

external interface UserData {
    val userId: Int
    val admin: Boolean
    val curator: Boolean
}

val globalContext = createContext<UserData?>(null)

fun main() {
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

class App : RComponent<RProps, RState>() {
    private fun initWithHistory(history: RouteResultHistory, replaceHomelink: Boolean = true) {
        if (replaceHomelink) {
            val homeLink = document.getElementById("home-link") as HTMLAnchorElement
            homeLink.onclick = {
                history.push("/")
                window.dispatchEvent(HashChangeEvent("hashchange"))
                it.preventDefault()
            }
        }

        manageNav()
    }

    override fun componentWillMount() {
        viewportMinWidthPolyfill()
    }

    override fun RBuilder.render() {
        browserRouter {
            switch {
                route<HomePageProps>("/", exact = true) {
                    initWithHistory(it.history)
                    child(HomePage::class) {
                        attrs.history = it.history
                    }
                }
                route<MapPageProps>("/beatsaver/:mapKey", exact = true) {
                    initWithHistory(it.history)
                    child(MapPage::class) {
                        attrs.history = it.history
                        attrs.mapKey = it.match.params.mapKey
                        attrs.beatsaver = true
                    }
                }
                route<MapPageProps>("/maps/:mapKey", exact = true) {
                    initWithHistory(it.history)
                    child(MapPage::class) {
                        attrs.history = it.history
                        attrs.mapKey = it.match.params.mapKey
                        attrs.beatsaver = false
                    }
                }
                route<RProps>("/upload", exact = true) {
                    initWithHistory(it.history)
                    child(UploadPage::class) {
                        attrs.history = it.history
                    }
                }
                route<ProfilePageProps>("/profile/:userId?", exact = true) {
                    initWithHistory(it.history)
                    globalContext.Consumer { userData ->
                        child(ProfilePage::class) {
                            key = "profile-${it.match.params.userId}"
                            attrs.userData = userData
                            attrs.history = it.history
                            attrs.userId = it.match.params.userId
                        }
                    }
                }
                route<RProps>("/playlists/new", exact = true) {
                    initWithHistory(it.history)
                    editPlaylist {
                        id = null
                        history = it.history
                    }
                }
                route<PlaylistProps>("/playlists/:id", exact = true) {
                    initWithHistory(it.history)
                    playlist {
                        id = it.match.params.id
                        history = it.history
                    }
                }
                route<PlaylistProps>("/playlists/:id/edit", exact = true) {
                    initWithHistory(it.history)
                    editPlaylist {
                        id = it.match.params.id
                        history = it.history
                    }
                }
                route<RProps>("/test", exact = true) {
                    initWithHistory(it.history)
                    recentTestplays { }
                }
                route<RProps>("/policy/dmca", exact = true) {
                    initWithHistory(it.history, false)
                    div {}
                }
                route<RProps>("/policy/tos", exact = true) {
                    initWithHistory(it.history, false)
                    div {}
                }
                route<RProps>("/policy/privacy", exact = true) {
                    initWithHistory(it.history, false)
                    div {}
                }
                route<RProps>("/mappers", exact = true) {
                    initWithHistory(it.history)
                    userList {
                        history = it.history
                    }
                }
                route<RProps>("/login", exact = true) {
                    initWithHistory(it.history)
                    loginPage { }
                }
                route<RProps>("/register", exact = true) {
                    initWithHistory(it.history)
                    signupPage { }
                }
                route<RProps>("/forgot", exact = true) {
                    initWithHistory(it.history)
                    forgotPage { }
                }
                route<ResetPageProps>("/reset/:jwt", exact = true) {
                    initWithHistory(it.history)
                    resetPage {
                        jwt = it.match.params.jwt
                        history = it.history
                    }
                }
                route<RProps>("/username", exact = true) {
                    initWithHistory(it.history)
                    pickUsernamePage {
                        history = it.history
                    }
                }
                route<RProps>("*") {
                    initWithHistory(it.history)
                    child(NotFound::class) { }
                }
            }
        }
    }
}
