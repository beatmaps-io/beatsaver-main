package io.beatmaps.user

import external.axiosGet
import io.beatmaps.UserData
import io.beatmaps.api.UserDetail
import io.beatmaps.common.Config
import io.beatmaps.common.formatTime
import io.beatmaps.index.ModalComponent
import io.beatmaps.index.beatmapTable
import io.beatmaps.index.modal
import io.beatmaps.playlist.playlistTable
import io.beatmaps.setPageTitle
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.html.js.onClickFunction
import org.w3c.dom.events.Event
import org.w3c.dom.get
import org.w3c.dom.set
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.createRef
import react.dom.a
import react.dom.b
import react.dom.br
import react.dom.div
import react.dom.h4
import react.dom.i
import react.dom.img
import react.dom.li
import react.dom.span
import react.dom.ul
import react.ref
import react.router.dom.RouteResultHistory
import react.router.dom.routeLink
import react.setState

external interface ProfilePageProps : RProps {
    var userData: UserData?
    var history: RouteResultHistory
    var userId: Int?
}

external interface ProfilePageState : RState {
    var loading: Boolean?
    var startup: Boolean?
    var userDetail: UserDetail?
    var state: ProfileTab
    var lastMapStateWip: Boolean?
    var notificationCount: Map<ProfileTab, Int>
}

enum class ProfileTab(val tabText: String, val condition: (ProfilePageProps, ProfilePageState) -> Boolean = { _, _ -> true }, val bootCondition: () -> Boolean = { false }, val onSelected: (ProfilePageProps) -> Unit = {}) {
    PUBLISHED("Published", bootCondition = { (localStorage["profile.showwip"] == "false") }, onSelected = { if (it.userId == null) { localStorage["profile.showwip"] = "false" } }),
    UNPUBLISHED("Unpublished", condition = { it, _ -> (it.userId == null) }, bootCondition = { (localStorage["profile.showwip"] == "true") }, onSelected = { if (it.userId == null) { localStorage["profile.showwip"] = "true" } }),
    PLAYLISTS("Playlists"),
    CURATED("Curated", condition = { _, it -> (it.userDetail?.curator == true) }),
    ACCOUNT("Account", condition = { it, _ -> (it.userData?.admin == true || it.userId == null) }),
    MODERATOR("Alerts", condition = { it, _ -> (it.userData?.admin == true || it.userId == null) })
}

class ProfilePage : RComponent<ProfilePageProps, ProfilePageState>() {
    private val modalRef = createRef<ModalComponent>()

    override fun componentWillMount() {
        setState {
            loading = false
            startup = false
            userDetail = null
            state = ProfileTab.UNPUBLISHED
            notificationCount = mapOf()
        }
    }

    override fun componentDidMount() {
        setPageTitle("Profile")

        loadState()
    }

    override fun componentWillUnmount() {
        window.removeEventListener("hashchange", ::onHashChange)
    }

    private fun onHashChange(it: Event) {
        val hash = window.location.hash.substring(1)
        val newState = ProfileTab.values().firstOrNull { hash == it.tabText.lowercase() && it.condition(props, state) } ?: state.state
        setState {
            state = newState
        }
    }

    private fun loadState() {
        setState {
            loading = true
        }

        val url = "${Config.apibase}/users" + (props.userId?.let { "/id/$it" } ?: "/me")

        axiosGet<UserDetail>(
            url
        ).then {
            setPageTitle("Profile - ${it.data.name}")
            setState {
                userDetail = it.data
                loading = false
            }
            setupTabState()
        }.catch {
            // Cancelled request
        }
    }

    private fun setupTabState() {
        val hash = window.location.hash.substring(1)
        setState {
            state = ProfileTab.values().firstOrNull {
                hash == it.tabText.lowercase() && it.condition(props, this)
            } ?: ProfileTab.values().firstOrNull { it.bootCondition() && it.condition(props, this) } ?: run {
                if (ProfileTab.UNPUBLISHED.condition(props, this)) {
                    ProfileTab.UNPUBLISHED
                } else {
                    ProfileTab.PUBLISHED
                }
            }

            startup = true
        }

        window.addEventListener("hashchange", ::onHashChange)
    }

    override fun RBuilder.render() {
        val loggedInLocal = props.userData?.userId
        modal {
            ref = modalRef
        }
        div("row") {
            div("col-md-4 mb-3") {
                div("card") {
                    div("card-body") {
                        div("d-flex flex-column align-items-center text-center") {
                            img("Profile Image", state.userDetail?.avatar, classes = "rounded-circle") {
                                attrs.width = "150"
                                attrs.height = "150"
                            }
                            div("mt-3") {
                                h4 {
                                    +(state.userDetail?.name ?: "")
                                }
                                /*p("text-muted mb-1") {
                                    +"Subheading"
                                }*/
                            }
                        }
                    }
                }
            }
            div("col-md-8 mb-3 position-relative") {
                div("card user-badges") {
                    div("card-body") {
                        state.userDetail?.stats?.let {
                            a("${Config.apibase}/users/id/${state.userDetail?.id ?: 0}/playlist", "_blank", "btn btn-secondary") {
                                attrs.attributes["download"] = ""
                                i("fas fa-list") { }
                                +"Playlist"
                            }
                            if (props.userData?.admin == true) {
                                routeLink("/modlog?user=${state.userDetail?.name}", className = "btn btn-secondary me-2") {
                                    i("fas fa-scroll") { }
                                    +"Mod Log"
                                }
                            }

                            +"Maps: ${it.totalMaps}, Upvotes: ${it.totalUpvotes}, Downvotes: ${it.totalDownvotes}"
                            br { }
                            +"Average BPM: ${it.avgBpm}, Average Score: ${it.avgScore}%, "
                            +"Average Duration: ${it.avgDuration.formatTime()}"
                            it.diffStats?.let { ds ->
                                br { }
                                b {
                                    +"Easy: "
                                }
                                +"${ds.easy}"
                                b {
                                    +", Normal: "
                                }
                                +"${ds.normal}"
                                b {
                                    +", Hard: "
                                }
                                +"${ds.hard}"
                                b {
                                    +", Expert: "
                                }
                                +"${ds.expert}"
                                b {
                                    +", Expert+: "
                                }
                                +"${ds.expertPlus}"
                            }
                        }
                        /*img("100 Maps", "https://cdn.discordapp.com/avatars/98334361564246016/01ade7513a63215bb7937d217b766da3.png", classes = "rounded-circle mx-2") {
                            attrs.width = "75"
                            attrs.height = "75"
                        }
                        img("100 Maps", "https://cdn.discordapp.com/avatars/98334361564246016/01ade7513a63215bb7937d217b766da3.png", classes = "rounded-circle mx-2") {
                            attrs.width = "75"
                            attrs.height = "75"
                        }
                        img("100 Maps", "https://cdn.discordapp.com/avatars/98334361564246016/01ade7513a63215bb7937d217b766da3.png", classes = "rounded-circle mx-2") {
                            attrs.width = "75"
                            attrs.height = "75"
                        }*/
                    }
                }
                ul("nav nav-tabs") {
                    ProfileTab.values().forEach { tab ->
                        if (!tab.condition(props, state)) return@forEach

                        li("nav-item") {
                            a("#", classes = "nav-link" + if (state.state == tab) " active" else "") {
                                key = tab.tabText
                                attrs.onClickFunction = {
                                    it.preventDefault()

                                    val userPart = if (props.userId != null) "/${props.userId}" else ""
                                    props.history.push("/profile$userPart#${tab.tabText.lowercase()}")

                                    tab.onSelected(props)
                                    setState {
                                        state = tab
                                    }
                                }

                                state.notificationCount.getOrElse(tab) { 0 }.let { notifCount ->
                                    if (notifCount > 0) {
                                        span("badge rounded-pill badge-danger me-2") {
                                            +"$notifCount"
                                        }
                                    }
                                }

                                span {
                                    +tab.tabText
                                }
                            }
                        }
                    }
                }
            }
        }
        val detail = state.userDetail
        if (ProfileTab.MODERATOR.condition(props, state)) {
            alertsPage {
                alertCountCallback = {
                    setState {
                        notificationCount = notificationCount.plus(ProfileTab.MODERATOR to it)
                    }
                }
                visible = state.state == ProfileTab.MODERATOR
                userId = props.userId
            }
        }

        if (detail != null) {
            playlistTable {
                own = props.userId == null
                userId = detail.id
                history = props.history
                visible = state.state == ProfileTab.PLAYLISTS
            }
            if (state.startup == true) {
                when (state.state) {
                    ProfileTab.UNPUBLISHED -> true
                    ProfileTab.PUBLISHED -> false
                    else -> null
                }?.let {
                    if (state.lastMapStateWip != it) {
                        setState {
                            lastMapStateWip = it
                        }
                    }
                }

                beatmapTable {
                    user = props.userId ?: loggedInLocal ?: 0
                    modal = modalRef
                    wip = state.lastMapStateWip == true
                    curated = state.state == ProfileTab.CURATED
                    history = props.history
                    visible = state.state == ProfileTab.UNPUBLISHED || state.state == ProfileTab.PUBLISHED || state.state == ProfileTab.CURATED
                }
            }
        }

        if (state.state == ProfileTab.ACCOUNT && detail != null) {
            if (props.userId == null) {
                account {
                    userDetail = detail
                }
            } else {
                adminAccount {
                    userDetail = detail
                }
            }
        }
    }
}
