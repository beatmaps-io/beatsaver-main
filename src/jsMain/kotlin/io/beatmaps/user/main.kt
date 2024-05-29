package io.beatmaps.user

import external.Axios
import external.axiosGet
import external.generateConfig
import external.routeLink
import io.beatmaps.Config
import io.beatmaps.UserData
import io.beatmaps.WithRouterProps
import io.beatmaps.api.UserDetail
import io.beatmaps.api.UserFollowData
import io.beatmaps.api.UserFollowRequest
import io.beatmaps.common.json
import io.beatmaps.index.ModalButton
import io.beatmaps.index.ModalComponent
import io.beatmaps.index.ModalData
import io.beatmaps.index.beatmapTable
import io.beatmaps.index.modal
import io.beatmaps.index.modalContext
import io.beatmaps.playlist.playlistTable
import io.beatmaps.setPageTitle
import io.beatmaps.shared.review.reviewTable
import io.beatmaps.util.textToContent
import io.beatmaps.util.userTitles
import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.html.InputType
import kotlinx.html.id
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import kotlinx.html.role
import kotlinx.html.title
import kotlinx.serialization.decodeFromString
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.dom.get
import org.w3c.dom.set
import react.RBuilder
import react.RComponent
import react.State
import react.createRef
import react.dom.a
import react.dom.b
import react.dom.br
import react.dom.button
import react.dom.div
import react.dom.h4
import react.dom.i
import react.dom.img
import react.dom.input
import react.dom.jsStyle
import react.dom.label
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
import react.key
import react.ref
import react.setState

external interface ProfilePageProps : WithRouterProps {
    var userData: UserData?
}

data class TabContext(val userId: Int?)

external interface ProfilePageState : State {
    var startup: Boolean?
    var userDetail: UserDetail?
    var state: ProfileTab?
    var notificationCount: Map<ProfileTab, Int>?
    var followData: UserFollowData?
    var followsDropdown: Boolean?
    var loading: Boolean?
}

enum class ProfileTab(val tabText: String, val condition: (ProfilePageProps, TabContext, ProfilePageState) -> Boolean = { _, _, _ -> true }, val bootCondition: () -> Boolean = { false }, val onSelected: (TabContext) -> Unit = {}) {
    PUBLISHED("Published", bootCondition = { (localStorage["profile.showwip"] == "false") }, onSelected = { if (it.userId == null) { localStorage["profile.showwip"] = "false" } }),
    UNPUBLISHED("Unpublished", condition = { _, c, _ -> (c.userId == null) }, bootCondition = { (localStorage["profile.showwip"] == "true") }, onSelected = { if (it.userId == null) { localStorage["profile.showwip"] = "true" } }),
    PLAYLISTS("Playlists"),
    CURATED("Curations", condition = { _, _, it -> (it.userDetail?.curatorTab == true) }),
    REVIEWS("Reviews"),
    ACCOUNT("Account", condition = { it, c, _ -> (it.userData?.admin == true || c.userId == null) })
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
        document.addEventListener("click", hideDropdown)

        loadState()
    }

    override fun componentDidUpdate(prevProps: ProfilePageProps, prevState: ProfilePageState, snapshot: Any) {
        if (prevProps.location.pathname != props.location.pathname || prevProps.params["userId"] != props.params["userId"]) {
            // Load new user
            loadState()
        }
    }

    override fun componentWillUnmount() {
        window.removeEventListener("hashchange", onHashChange)
        document.removeEventListener("click", hideDropdown)
    }

    private val onHashChange = { _: Event? ->
        val hash = window.location.hash.substring(1)
        val tabContext = TabContext(props.params["userId"]?.toIntOrNull())
        val newState = ProfileTab.values().firstOrNull { hash == it.tabText.lowercase() && it.condition(props, tabContext, state) } ?: state.state
        setState {
            state = newState
        }
    }

    private val hideDropdown = { _: Event? ->
        setState {
            followsDropdown = false
        }
    }

    private fun loadState() {
        modalRef.current?.hide()
        val userId = props.params["userId"]?.toIntOrNull()
        val url = "${Config.apibase}/users" + (userId?.let { "/id/$it" } ?: "/me")

        setState {
            userDetail = null
            followData = null
            startup = false
        }

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
        val tabContext = TabContext(props.params["userId"]?.toIntOrNull())
        setState {
            state = ProfileTab.values().firstOrNull {
                hash == it.tabText.lowercase() && it.condition(props, tabContext, this)
            } ?: ProfileTab.values().firstOrNull { it.bootCondition() && it.condition(props, tabContext, this) } ?: run {
                if (ProfileTab.UNPUBLISHED.condition(props, tabContext, this)) {
                    ProfileTab.UNPUBLISHED
                } else {
                    ProfileTab.PUBLISHED
                }
            }

            startup = true
        }

        window.addEventListener("hashchange", onHashChange)
    }

    private fun setFollowStatus(following: Boolean, upload: Boolean, curation: Boolean) {
        setState { loading = true }
        val req = UserFollowRequest(state.userDetail?.id ?: 0, following, upload, curation)
        Axios.post<UserFollowRequest>("${Config.apibase}/users/follow", req, generateConfig<UserFollowRequest, String>()).then({
            setState {
                loading = false
                followData = UserFollowData(
                    (followData?.followers ?: 0).let {
                        if (followData?.following == following) {
                            it
                        } else if (following) {
                            it + 1
                        } else {
                            it - 1
                        }
                    },
                    followData?.follows,
                    following,
                    upload,
                    curation
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
                        attrs.scrollParent = it
                        attrs.following = following
                        attrs.followedBy = followedBy
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

        modalContext.Provider {
            attrs.value = modalRef

            div("row") {
                div("col-md-4 mb-3") {
                    div("card user-info" + if (state.userDetail?.patreon?.supporting == true) " border border-3 border-patreon" else "") {
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
                                div {
                                    state.userDetail?.playlistUrl?.let { url ->
                                        div("btn-group") {
                                            a(url, "_blank", "btn btn-secondary") {
                                                attrs.attributes["download"] = ""
                                                i("fas fa-download") { }
                                                +"Playlist"
                                            }
                                            a(
                                                "bsplaylist://playlist/$url/beatsaver-user-${state.userDetail?.id}.bplist",
                                                classes = "btn btn-primary"
                                            ) {
                                                attrs.attributes["aria-label"] = "One-Click"
                                                i("fas fa-cloud-download-alt m-0") { }
                                            }
                                        }
                                    }
                                    if (loggedInLocal != null && loggedInLocal != state.userDetail?.id) {
                                        state.followData?.let { fd ->
                                            div("btn-group") {
                                                val btnClasses = "btn btn-" + if (fd.following) "secondary" else "primary"
                                                button(classes = btnClasses) {
                                                    attrs.disabled = state.loading == true
                                                    attrs.onClickFunction = { e ->
                                                        e.preventDefault()
                                                        setFollowStatus(!fd.following, !fd.following, !fd.following)
                                                    }

                                                    if (fd.following) {
                                                        i("fas fa-user-minus") { }
                                                        +"Unfollow"
                                                    } else {
                                                        i("fas fa-user-plus") { }
                                                        +"Follow"
                                                    }
                                                }
                                                div("btn-group") {
                                                    button(classes = "dropdown-toggle $btnClasses") {
                                                        attrs.onClickFunction = {
                                                            it.stopPropagation()
                                                            setState {
                                                                followsDropdown = followsDropdown != true
                                                            }
                                                        }
                                                    }
                                                    div("dropdown-menu mt-4" + if (state.followsDropdown == true) " show" else "") {
                                                        label("dropdown-item") {
                                                            attrs.htmlFor = "follow-uploads"
                                                            attrs.role = "button"
                                                            attrs.onClickFunction = {
                                                                it.stopPropagation()
                                                            }
                                                            input(InputType.checkBox, classes = "form-check-input me-2") {
                                                                attrs.id = "follow-uploads"
                                                                attrs.disabled = state.loading == true
                                                                attrs.checked = fd.upload
                                                                attrs.onChangeFunction = { ev ->
                                                                    val newUpload = (ev.target as HTMLInputElement).checked
                                                                    setFollowStatus(fd.following || newUpload || fd.curation, newUpload, fd.curation)
                                                                }
                                                            }
                                                            +"Uploads"
                                                        }
                                                        label("dropdown-item") {
                                                            attrs.htmlFor = "follow-curations"
                                                            attrs.role = "button"
                                                            attrs.onClickFunction = {
                                                                it.stopPropagation()
                                                            }
                                                            input(InputType.checkBox, classes = "form-check-input me-2") {
                                                                attrs.id = "follow-curations"
                                                                attrs.disabled = state.loading == true
                                                                attrs.checked = fd.curation
                                                                attrs.onChangeFunction = { ev ->
                                                                    val newCuration = (ev.target as HTMLInputElement).checked
                                                                    setFollowStatus(fd.following || fd.upload || newCuration, fd.upload, newCuration)
                                                                }
                                                            }
                                                            +"Curations"
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (props.userData?.admin == true || props.userData?.curator == true) {
                                    div("mt-2") {
                                        div("btn-group") {
                                            if (props.userData?.admin == true) {
                                                routeLink("/modlog?user=${state.userDetail?.name}", className = "btn btn-secondary") {
                                                    i("fas fa-scroll") { }
                                                    +"Mod Log"
                                                }
                                            }
                                            routeLink("/modreview?user=${state.userDetail?.name}", className = "btn btn-secondary") {
                                                i("fas fa-heartbeat") { }
                                                +"Review Log"
                                            }
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
                            if (state.userDetail?.suspendedAt != null) {
                                span("text-danger") {
                                    +"This user has been suspended."
                                }
                            } else {
                                span {
                                    textToContent((state.userDetail?.description?.take(500) ?: ""))
                                }
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
            val userId = props.params["userId"]?.toIntOrNull()
            ul("nav nav-minimal mb-3") {
                val tabContext = TabContext(userId)
                ProfileTab.entries.forEach { tab ->
                    if (!tab.condition(props, tabContext, state)) return@forEach

                    li("nav-item") {
                        a("#", classes = "nav-link" + if (state.state == tab) " active" else "") {
                            key = tab.tabText
                            attrs.onClickFunction = {
                                it.preventDefault()

                                val userPart = if (userId != null) "/$userId" else ""
                                props.history.push("/profile$userPart#${tab.tabText.lowercase()}")

                                tab.onSelected(tabContext)
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
                    attrs.userId = detail.id
                    attrs.visible = state.state == ProfileTab.PLAYLISTS
                }
                if (state.startup == true) {
                    beatmapTable {
                        attrs.key = "maps-${state.state}"
                        attrs.user = userId ?: loggedInLocal ?: 0
                        attrs.wip = state.state == ProfileTab.UNPUBLISHED
                        attrs.curated = state.state == ProfileTab.CURATED
                        attrs.visible = state.state == ProfileTab.UNPUBLISHED || state.state == ProfileTab.PUBLISHED || state.state == ProfileTab.CURATED
                    }
                }
            }

            if (state.state == ProfileTab.REVIEWS) {
                reviewTable {
                    attrs.userDetail = detail
                    // There may be collaborators passed here, however they are not passed here as they are not required
                    // And would just result in the purposeless loading of additional data
                }
            }

            if (state.state == ProfileTab.ACCOUNT && detail != null) {
                if (userId == null) {
                    accountTab {
                        attrs.key = "account"
                        attrs.userDetail = detail
                        attrs.onUpdate = { loadState() }
                    }
                } else {
                    adminAccount {
                        attrs {
                            userDetail = detail
                            onUpdate = { loadState() }
                        }
                    }
                }
            }
        }
    }
}
