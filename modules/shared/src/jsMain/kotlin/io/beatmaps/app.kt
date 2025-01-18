package io.beatmaps

import io.beatmaps.admin.admin
import io.beatmaps.api.ReviewDetail
import io.beatmaps.api.ReviewReplyDetail
import io.beatmaps.index.homePage
import io.beatmaps.issues.issuesPage
import io.beatmaps.maps.mapEmbed
import io.beatmaps.maps.mapPage
import io.beatmaps.maps.testplay.testplayModule
import io.beatmaps.nav.viewportMinWidthPolyfill
import io.beatmaps.playlist.playlists
import io.beatmaps.upload.uploadPage
import io.beatmaps.user.alerts.alertsPage
import io.beatmaps.user.profilePage
import io.beatmaps.user.user
import io.beatmaps.util.fcmemo
import js.objects.jso
import react.Props
import react.createElement
import react.dom.client.createRoot
import react.dom.html.ReactHTML.div
import react.router.dom.RouterProvider
import react.router.dom.createBrowserRouter
import react.useEffectOnce
import web.dom.document
import web.events.EventHandler
import web.window.window

fun setPageTitle(page: String) {
    document.title = "BeatSaver - $page"
}

@EagerInitialization
@OptIn(ExperimentalStdlibApi::class)
@Suppress("unused")
private val init = init()

private fun init() {
    window.onload = EventHandler {
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
                beatsaver = true
            }
        },
        bsroute("/maps/:mapKey") {
            mapPage {
                beatsaver = false
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
            playlists.feed { }
        },
        bsroute("/playlists/new") {
            playlists.edit { }
        },
        bsroute("/playlists/:id") {
            playlists.page { }
        },
        bsroute("/playlists/:id/edit") {
            playlists.edit { }
        },
        bsroute("/playlists/:id/add") {
            playlists.multiAdd { }
        },
        bsroute("/test") {
            testplayModule.recentTestplays { }
        },
        bsroute("/modlog") {
            admin.modLog { }
        },
        bsroute("/modreview") {
            admin.modReview {
                type = ReviewDetail::class
            }
        },
        bsroute("/modreply") {
            admin.modReview {
                type = ReviewReplyDetail::class
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
            user.userList { }
        },
        bsroute("/login") {
            user.login { }
        },
        bsroute("/oauth2/authorize") {
            user.authorize { }
        },
        bsroute("/register") {
            user.register { }
        },
        bsroute("/forgot") {
            user.forgot { }
        },
        bsroute("/reset/:jwt") {
            user.reset { }
        },
        bsroute("/change-email/:jwt") {
            user.changeEmail { }
        },
        bsroute("/username") {
            user.username { }
        },
        bsroute("/quest") {
            user.quest { }
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

val app = fcmemo<Props>("BeatSaver Root") {
    useEffectOnce {
        viewportMinWidthPolyfill()
    }

    provide(configContext, "config-data") {
        provide(globalContext, "user-data") {
            RouterProvider {
                router = appRouter
                future = jso {
                    v7_startTransition = true
                    v7_relativeSplatPath = false
                }
            }
        }
    }
}
