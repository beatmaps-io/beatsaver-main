package io.beatmaps.user

import external.Axios
import external.axiosGet
import external.generateConfig
import io.beatmaps.UserData
import io.beatmaps.api.UserDetail
import io.beatmaps.api.UserFollowData
import io.beatmaps.api.UserFollowRequest
import io.beatmaps.common.Config
import io.beatmaps.common.json
import io.beatmaps.index.ModalButton
import io.beatmaps.index.ModalComponent
import io.beatmaps.index.ModalData
import io.beatmaps.index.beatmapTable
import io.beatmaps.index.modal
import io.beatmaps.playlist.playlistTable
import io.beatmaps.setPageTitle
import io.beatmaps.util.textToContent
import io.beatmaps.util.userTitles
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import kotlinx.serialization.decodeFromString
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
import react.dom.jsStyle
import react.dom.li
import react.dom.p
import react.dom.span
import react.dom.table
import react.dom.tbody
import react.dom.td
import react.dom.th
import react.dom.thead
import react.dom.tr
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
    var startup: Boolean?
    var userDetail: UserDetail?
    var state: ProfileTab?
    var lastMapStateWip: Boolean?
    var notificationCount: Map<ProfileTab, Int>?
    var followData: UserFollowData?
}

enum class ProfileTab(val tabText: String, val condition: (ProfilePageProps, ProfilePageState) -> Boolean = { _, _ -> true }, val bootCondition: () -> Boolean = { false }, val onSelected: (ProfilePageProps) -> Unit = {}) {
    PUBLISHED("Published", bootCondition = { (localStorage["profile.showwip"] == "false") }, onSelected = { if (it.userId == null) { localStorage["profile.showwip"] = "false" } }),
    UNPUBLISHED("Unpublished", condition = { it, _ -> (it.userId == null) }, bootCondition = { (localStorage["profile.showwip"] == "true") }, onSelected = { if (it.userId == null) { localStorage["profile.showwip"] = "true" } }),
    PLAYLISTS("Playlists"),
    CURATED("Curated", condition = { _, it -> (it.userDetail?.curator == true) }),
    ACCOUNT("Account", condition = { it, _ -> (it.userData?.admin == true || it.userId == null) })
}

class ProfilePage : RComponent<ProfilePageProps, ProfilePageState>() {
    private val modalRef = createRef<ModalComponent>()

    override fun componentWillMount() {
        setState {
            notificationCount = mapOf()
        }
    }

    override fun componentDidMount() {
        setPageTitle("Profile")

        loadState()
    }

    override fun componentWillUnmount() {
        window.removeEventListener("hashchange", onHashChange)
    }

    private val onHashChange = { _: Event? ->
        val hash = window.location.hash.substring(1)
        val newState = ProfileTab.values().firstOrNull { hash == it.tabText.lowercase() && it.condition(props, state) } ?: state.state
        setState {
            state = newState
        }
    }

    private fun loadState() {
        val url = "${Config.apibase}/users" + (props.userId?.let { "/id/$it" } ?: "/me")

        axiosGet<String>(
            url
        ).then {
            // Decode is here so that 401 actually passes to error handler
            val data = json.decodeFromString<UserDetail>(it.data)

            setPageTitle("Profile - ${data.name}")
            setState {
                userDetail = data
                followData = data.followData
            }
            setupTabState()
        }.catch {
            if (it.asDynamic().response?.status == 401) {
                props.history.push("/login")
            }
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

        window.addEventListener("hashchange", onHashChange)
    }

    private fun setFollowStatus(followed: Boolean) {
        Axios.post<UserFollowRequest>("${Config.apibase}/users/follow", UserFollowRequest(state.userDetail?.id ?: 0, followed), generateConfig<UserFollowRequest, String>()).then({
            setState {
                followData = UserFollowData(
                    (followData?.followers ?: 0).let {
                        if (followed) it + 1 else it - 1
                    },
                    followData?.follows,
                    followed
                )
            }
        }) { }
    }

    private fun showFollows(title: String, following: Int? = null, followedBy: Int? = null) {
        modalRef.current?.showDialog(
            ModalData(
                title,
                bodyCallback = {
                    followList {
                        scrollParent = it
                        this.following = following
                        this.followedBy = followedBy
                    }
                },
                buttons = listOf(
                    ModalButton("Close")
                )
            )
        )
    }

    override fun RBuilder.render() {
        val loggedInLocal = props.userData?.userId
        modal {
            ref = modalRef
        }
        div("row") {
            div("col-md-4 mb-3") {
                div("card user-info") {
                    div("card-body") {
                        div("d-flex align-items-center mb-2") {
                            img("Profile Image", state.userDetail?.avatar, classes = "rounded-circle me-3") {
                                attrs.width = "50"
                                attrs.height = "50"
                            }
                            div("d-inline") {
                                h4("mb-1") {
                                    +(state.userDetail?.name ?: "")
                                }
                                p("text-muted mb-1") {
                                    +userTitles(state.userDetail).joinToString(", ")
                                }
                            }
                        }
                        div("mb-3") {
                            state.followData?.followers?.let {
                                span {
                                    a(if (it > 0) "#" else null) {
                                        attrs.onClickFunction = { e ->
                                            e.preventDefault()
                                            if (it > 0) {
                                                showFollows("Followers", following = state.userDetail?.id)
                                            }
                                        }

                                        +"Followed by "
                                        b("text-warning") { +"$it" }
                                        +(" user" + if (it != 1) "s" else "")
                                    }
                                }
                            }
                            state.followData?.follows?.let {
                                br { }
                                span {
                                    a(if (it > 0) "#" else null) {
                                        attrs.onClickFunction = { e ->
                                            e.preventDefault()
                                            if (it > 0) {
                                                showFollows("Follows", followedBy = state.userDetail?.id)
                                            }
                                        }

                                        +"Following "
                                        b("text-warning") { +"$it" }
                                        +(" user" + if (it != 1) "s" else "")
                                    }
                                }
                            }
                        }
                        div {
                            a("${Config.apibase}/users/id/${state.userDetail?.id ?: 0}/playlist", "_blank", "btn btn-secondary") {
                                attrs.attributes["download"] = ""
                                i("fas fa-download") { }
                                +"Playlist"
                            }
                            if (props.userData?.admin == true) {
                                routeLink("/modlog?user=${state.userDetail?.name}", className = "btn btn-secondary") {
                                    i("fas fa-scroll") { }
                                    +"Mod Log"
                                }
                            }
                            state.followData?.following?.let { following ->
                                a("#", classes = "btn btn-" + if (following) "secondary" else "primary") {
                                    attrs.onClickFunction = { e ->
                                        e.preventDefault()
                                        setFollowStatus(!following)
                                    }

                                    if (following) {
                                        i("fas fa-user-minus") { }
                                        +"Unfollow"
                                    } else {
                                        i("fas fa-user-plus") { }
                                        +"Follow"
                                    }
                                }
                            }
                        }
                    }
                }
            }
            div("col-md-8 mb-3 position-relative") {
                div("card user-info") {
                    div("card-body") {
                        span {
                            textToContent((state.userDetail?.description?.take(500) ?: ""))
                        }
                        state.userDetail?.stats?.let {
                            if (it.totalMaps != 0) {
                                table("table table-dark") {
                                    thead {
                                        tr {
                                            th { +"Maps" }
                                            th { +"Average Rating" }
                                            th { +"Difficulty Spread" }
                                        }
                                    }
                                    tbody {
                                        tr {
                                            td { +"${it.totalMaps}" }
                                            td { +"${it.avgScore}% (${it.totalUpvotes} / ${it.totalDownvotes})" }
                                            it.diffStats?.let { ds ->
                                                td {
                                                    div("difficulty-spread mb-1") {
                                                        div("badge-green") {
                                                            attrs.jsStyle {
                                                                flex = ds.easy
                                                            }
                                                            attrs.title = "${ds.easy}"
                                                        }
                                                        div("badge-blue") {
                                                            attrs.jsStyle {
                                                                flex = ds.normal
                                                            }
                                                            attrs.title = "${ds.normal}"
                                                        }
                                                        div("badge-hard") {
                                                            attrs.jsStyle {
                                                                flex = ds.hard
                                                            }
                                                            attrs.title = "${ds.hard}"
                                                        }
                                                        div("badge-expert") {
                                                            attrs.jsStyle {
                                                                flex = ds.expert
                                                            }
                                                            attrs.title = "${ds.expert}"
                                                        }
                                                        div("badge-purple") {
                                                            attrs.jsStyle {
                                                                flex = ds.expertPlus
                                                            }
                                                            attrs.title = "${ds.expertPlus}"
                                                        }
                                                    }
                                                    div("legend") {
                                                        span("legend-green") { +"Easy" }
                                                        span("legend-blue") { +"Normal" }
                                                        span("legend-hard") { +"Hard" }
                                                        span("legend-expert") { +"Expert" }
                                                        span("legend-purple") { +"Expert+" }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        ul("nav nav-pills mb-3") {
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

                        (state.notificationCount?.get(tab) ?: 0).let { notifCount ->
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
        val detail = state.userDetail
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
                    onUpdate = { loadState() }
                }
            } else {
                adminAccount {
                    userDetail = detail
                }
            }
        }
    }
}
