package io.beatmaps

import io.beatmaps.api.ReviewDetail
import io.beatmaps.api.ReviewReplyDetail
import io.beatmaps.common.json
import io.beatmaps.index.homePage
import io.beatmaps.issues.issuesPage
import io.beatmaps.maps.mapEmbed
import io.beatmaps.maps.mapPage
import io.beatmaps.admin.admin
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
import io.beatmaps.user.list.userList
import io.beatmaps.user.loginPage
import io.beatmaps.user.oauth.authorizePage
import io.beatmaps.user.pickUsernamePage
import io.beatmaps.user.profilePage
import io.beatmaps.user.resetPage
import io.beatmaps.user.signupPage
import js.objects.jso
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
import io.beatmaps.maps.testplay.testplayModule
import web.dom.document

fun setPageTitle(page: String) {
    document.title = "BeatSaver - $page"
}

@Serializable
data class UserData(
    val userId: Int = 0,
    val admin: Boolean = false,
    val curator: Boolean = false,
    val suspended: Boolean = false
)

@Serializable
data class ConfigData(
    // Safe because if captchas are bypassed the backend will still reject requests
    val showCaptcha: Boolean = true,
    val v2Search: Boolean = false,
    val captchaProvider: String = "Fake"
)

val globalContext = createContext<UserData?>(null)
val configContext = createContext<ConfigData?>(null)

object Config {
    const val apibase = "/api"
    const val dateFormat = "YYYY-MM-DD"
}

@EagerInitialization
@OptIn(ExperimentalStdlibApi::class)
@Suppress("unused")
private val init = init()

private fun init() {
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
        bsroute("/maps/:mapKey/embed") {
            mapEmbed { }
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
            testplayModule.recentTestplays { }
        },
        bsroute("/modlog") {
            admin.modLog { }
        },
        bsroute("/modreview") {
            admin.modReview {
                attrs.type = ReviewDetail::class
            }
        },
        bsroute("/modreply") {
            admin.modReview {
                attrs.type = ReviewReplyDetail::class
            }
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
        bsroute("/issues") {
            admin.issueList { }
        },
        bsroute("/issues/:id") {
            issuesPage { }
        },
        bsroute("*") {
            notFound { }
        }
    )
)

val app = fc<Props>("BeatSaver Root") {
    useEffectOnce {
        viewportMinWidthPolyfill()
    }

    configContext.Provider {
        attrs.value = document.getElementById("config-data")?.let {
            json.decodeFromString<ConfigData>(it.textContent ?: "{}")
        }

        globalContext.Provider {
            attrs.value = document.getElementById("user-data")?.let {
                json.decodeFromString<UserData>(it.textContent ?: "{}")
            }

            RouterProvider {
                attrs.router = appRouter
                attrs.future = jso {
                    v7_startTransition = true
                    v7_relativeSplatPath = false
                }
            }
        }
    }
}
