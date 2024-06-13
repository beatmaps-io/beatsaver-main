package io.beatmaps

import external.ReactDatesInit
import io.beatmaps.common.json
import io.beatmaps.index.homePage
import io.beatmaps.maps.mapPage
import io.beatmaps.maps.recent.recentTestplays
import io.beatmaps.modlog.modlog
import io.beatmaps.modreview.modReview
import io.beatmaps.nav.viewportMinWidthPolyfill
import io.beatmaps.playlist.editPlaylist
import io.beatmaps.playlist.multiAddPlaylist
import io.beatmaps.playlist.playlistFeed
import io.beatmaps.playlist.playlistPage
import io.beatmaps.quest.quest
import io.beatmaps.upload.uploadPage
import io.beatmaps.user.alerts.alertsPage
import io.beatmaps.user.changeEmailPage
import io.beatmaps.user.forgotPage
import io.beatmaps.user.loginPage
import io.beatmaps.user.oauth.authorizePage
import io.beatmaps.user.pickUsernamePage
import io.beatmaps.user.profilePage
import io.beatmaps.user.resetPage
import io.beatmaps.user.signupPage
import io.beatmaps.user.userList
import kotlinx.browser.window
import kotlinx.serialization.Serializable
import react.Props
import react.createContext
import react.createElement
import react.dom.client.createRoot
import react.dom.div
import react.fc
import react.router.dom.RouterProvider
import react.router.dom.createBrowserRouter
import react.useEffectOnce
import web.dom.document

fun setPageTitle(page: String) {
    document.title = "BeatSaver - $page"
}

@Serializable
data class UserData(val userId: Int = 0, val admin: Boolean = false, val curator: Boolean = false, val suspended: Boolean = false)

val globalContext = createContext<UserData?>(null)

object Config {
    const val apibase = "/api"
    const val dateFormat = "YYYY-MM-DD"
}

fun main() {
    ReactDatesInit // This actually needs to be referenced I guess
    window.onload = {
        document.getElementById("root")?.let { root ->
            createRoot(root).render(createElement(app))
        }
    }
}

val appRouter = createBrowserRouter(
    arrayOf(
        bsroute("/") {
            homePage { }
        },
        bsroute("/beatsaver/:mapKey") {
            mapPage {
                attrs.beatsaver = true
            }
        },
        bsroute("/maps/:mapKey") {
            mapPage {
                attrs.beatsaver = false
            }
        },
        bsroute("/upload") {
            uploadPage { }
        },
        bsroute("/profile") {
            profilePage { }
        },
        bsroute("/profile/:userId") {
            profilePage { }
        },
        bsroute("/alerts") {
            alertsPage { }
        },
        bsroute("/playlists") {
            playlistFeed { }
        },
        bsroute("/playlists/new") {
            editPlaylist { }
        },
        bsroute("/playlists/:id") {
            playlistPage { }
        },
        bsroute("/playlists/:id/edit") {
            editPlaylist { }
        },
        bsroute("/playlists/:id/add") {
            multiAddPlaylist { }
        },
        bsroute("/test") {
            recentTestplays { }
        },
        bsroute("/modlog") {
            modlog { }
        },
        bsroute("/modreview") {
            modReview { }
        },
        bsroute("/policy/dmca", replaceHomelink = false) {
            div {}
        },
        bsroute("/policy/tos", replaceHomelink = false) {
            div {}
        },
        bsroute("/policy/privacy", replaceHomelink = false) {
            div {}
        },
        bsroute("/mappers") {
            userList { }
        },
        bsroute("/login") {
            loginPage { }
        },
        bsroute("/oauth2/authorize") {
            authorizePage { }
        },
        bsroute("/register") {
            signupPage { }
        },
        bsroute("/forgot") {
            forgotPage { }
        },
        bsroute("/reset/:jwt") {
            resetPage { }
        },
        bsroute("/change-email/:jwt") {
            changeEmailPage { }
        },
        bsroute("/username") {
            pickUsernamePage { }
        },
        bsroute("/quest") {
            quest { }
        },
        bsroute("*") {
            notFound { }
        }
    )
)

val app = fc<Props> {
    useEffectOnce {
        viewportMinWidthPolyfill()
    }

    globalContext.Provider {
        attrs.value = kotlinx.browser.document.getElementById("user-data")?.let {
            json.decodeFromString<UserData>(it.textContent ?: "{}")
        }

        RouterProvider {
            attrs.router = appRouter
        }
    }
}
