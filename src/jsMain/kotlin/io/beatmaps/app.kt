package io.beatmaps
import external.Moment
import io.beatmaps.index.HomePage
import io.beatmaps.index.HomePageProps
import io.beatmaps.maps.MapPage
import io.beatmaps.maps.MapPageProps
import io.beatmaps.maps.UploadPage
import io.beatmaps.maps.recent.recentTestplays
import io.beatmaps.nav.manageNav
import io.beatmaps.nav.viewportMinWidthPolyfill
import io.beatmaps.user.AlertsPage
import io.beatmaps.user.BeatsaverPage
import io.beatmaps.user.ProfilePage
import io.beatmaps.user.ProfilePageProps
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLAnchorElement
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.dom.*
import react.router.dom.RouteResultHistory
import react.router.dom.browserRouter
import react.router.dom.route
import react.router.dom.switch
import kotlinext.js.require

fun setPageTitle(page: String) {
    document.title = "BeatMaps.io - $page"
}

fun main() {
    require("react-dates/lib/css/_datepicker.css")
    window.onload = {
        val root = document.getElementById("root")
        render(root) {
            child(App::class) { }
        }
    }
}

@JsExport
class App : RComponent<RProps, RState>() {
    private fun initWithHistory(history: RouteResultHistory, replaceHomelink: Boolean = true) {
        if (replaceHomelink) {
            val homeLink = document.getElementById("home-link") as HTMLAnchorElement
            homeLink.onclick = {
                history.push("/")
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
                    child (UploadPage::class) {
                        attrs.history = it.history
                    }
                }
                route<ProfilePageProps>("/profile/:userId?", exact = true) {
                    initWithHistory(it.history)
                    child(ProfilePage::class) {
                        attrs.history = it.history
                        attrs.userId = it.match.params.userId
                    }
                }
                route<RProps>("/test", exact = true) {
                    initWithHistory(it.history)
                    recentTestplays {

                    }
                }
                route<RProps>("/alerts", exact = true) {
                    initWithHistory(it.history)
                    child(AlertsPage::class) {

                    }
                }
                route<RProps>("/beatsaver", exact = true) {
                    initWithHistory(it.history)
                    child(BeatsaverPage::class) {
                        attrs.history = it.history
                    }
                }
                route<RProps>("/policy/dmca", exact = true) {
                    initWithHistory(it.history, false)
                    div {}
                }
                route<RProps>("*") {
                    initWithHistory(it.history)
                    child(NotFound::class) {

                    }
                }
            }
        }
    }
}