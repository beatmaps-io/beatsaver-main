package io.beatmaps.user

import external.Axios
import external.IReCAPTCHA
import external.axiosGet
import external.generateConfig
import external.recaptcha
import external.routeLink
import io.beatmaps.Config
import io.beatmaps.History
import io.beatmaps.UserData
import io.beatmaps.api.IssueCreationRequest
import io.beatmaps.api.UserDetail
import io.beatmaps.api.UserFollowData
import io.beatmaps.api.UserFollowRequest
import io.beatmaps.common.SearchOrder
import io.beatmaps.common.SortOrderTarget
import io.beatmaps.common.api.UserReportData
import io.beatmaps.common.json
import io.beatmaps.globalContext
import io.beatmaps.index.ModalButton
import io.beatmaps.index.ModalComponent
import io.beatmaps.index.ModalData
import io.beatmaps.index.beatmapTable
import io.beatmaps.index.modal
import io.beatmaps.index.modalContext
import io.beatmaps.playlist.playlistTable
import io.beatmaps.setPageTitle
import io.beatmaps.shared.review.reviewTable
import io.beatmaps.shared.search.sort
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
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.Event
import org.w3c.dom.get
import org.w3c.dom.set
import react.Props
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
import react.dom.textarea
import react.dom.th
import react.dom.thead
import react.dom.tr
import react.dom.ul
import react.fc
import react.ref
import react.router.useLocation
import react.router.useNavigate
import react.router.useParams
import react.useContext
import react.useEffect
import react.useEffectOnce
import react.useRef
import react.useState

data class TabContext(val userId: Int?)

enum class ProfileTab(val tabText: String, val condition: (UserData?, TabContext, UserDetail?) -> Boolean = { _, _, _ -> true }, val bootCondition: () -> Boolean = { false }, val onSelected: (TabContext) -> Unit = {}) {
    PUBLISHED("Published", bootCondition = { (localStorage["profile.showwip"] == "false") }, onSelected = { if (it.userId == null) { localStorage["profile.showwip"] = "false" } }),
    UNPUBLISHED("Unpublished", condition = { _, c, _ -> (c.userId == null) }, bootCondition = { (localStorage["profile.showwip"] == "true") }, onSelected = { if (it.userId == null) { localStorage["profile.showwip"] = "true" } }),
    PLAYLISTS("Playlists"),
    CURATED("Curations", condition = { _, _, it -> (it?.curatorTab == true) }),
    REVIEWS("Reviews"),
    ACCOUNT("Account", condition = { it, c, _ -> (it?.admin == true || c.userId == null) })
}

val profilePage = fc<Props> { _ ->
    val captchaRef = useRef<IReCAPTCHA>()
    val reasonRef = useRef<HTMLTextAreaElement>()
    val modalRef = useRef<ModalComponent>()
    val userData = useContext(globalContext)

    val (startup, setStartup) = useState(false)
    val (userDetail, setUserDetail) = useState<UserDetail>()
    val (tabState, setTabState) = useState<ProfileTab>()
    val (followData, setFollowData) = useState<UserFollowData>()
    val (followsDropdown, setFollowsDropdown) = useState(false)
    val (loading, setLoading) = useState(false)
    val publishedSortOrder = useState(SearchOrder.Relevance)
    val curationsSortOrder = useState(SearchOrder.Curated)
    val (sortOrder, setSortOrder) = if (tabState == ProfileTab.CURATED) curationsSortOrder else publishedSortOrder

    val location = useLocation()
    val history = History(useNavigate())
    val params = useParams()

    fun setupTabState(user: UserDetail) {
        val hash = location.hash.substring(1)
        val tabContext = TabContext(params["userId"]?.toIntOrNull())
        val newState = ProfileTab.entries.firstOrNull {
            hash == it.tabText.lowercase() && it.condition(userData, tabContext, user)
        } ?: ProfileTab.entries.firstOrNull { it.bootCondition() && it.condition(userData, tabContext, user) } ?: run {
            if (ProfileTab.UNPUBLISHED.condition(userData, tabContext, user)) {
                ProfileTab.UNPUBLISHED
            } else {
                ProfileTab.PUBLISHED
            }
        }

        setTabState(newState)
        setStartup(true)
    }

    fun loadState() {
        modalRef.current?.hide()
        val userId = params["userId"]?.toIntOrNull()
        val url = "${Config.apibase}/users" + (userId?.let { "/id/$it" } ?: "/me")

        setUserDetail(null)
        setFollowData(null)
        setStartup(false)

        axiosGet<String>(url).then {
            // Decode is here so that 401 actually passes to error handler
            val data = json.decodeFromString<UserDetail>(it.data)

            setPageTitle("Profile - ${data.name}")
            setUserDetail(data)
            setFollowData(data.followData)
            setupTabState(data)
        }.catch {
            if (it.asDynamic().response?.status == 401) {
                history.push("/login")
            }
        }
    }

    fun showFollows(title: String, following: Int? = null, followedBy: Int? = null) {
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

    fun setFollowStatus(following: Boolean, upload: Boolean, curation: Boolean, collab: Boolean) {
        setLoading(true)
        val req = UserFollowRequest(userDetail?.id ?: 0, following, upload, curation, collab)

        Axios.post<UserFollowRequest>("${Config.apibase}/users/follow", req, generateConfig<UserFollowRequest, String>()).then({
            setLoading(false)
            setFollowData(
                UserFollowData(
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
                    curation,
                    collab
                )
            )
        }) { }
    }

    fun report(userId: Int) {
        captchaRef.current?.let { cc ->
            setLoading(true)
            cc.executeAsync().then { captcha ->
                val reason = reasonRef.current?.value?.trim() ?: ""
                Axios.post<String>(
                    "${Config.apibase}/issues/create",
                    IssueCreationRequest(captcha, reason, UserReportData(userId)),
                    generateConfig<IssueCreationRequest, String>(validStatus = arrayOf(201))
                ).then {
                    history.push("/issues/${it.data}")
                }
            }.finally {
                setLoading(false)
            }
        }
    }

    useEffectOnce {
        val hideDropdown = { _: Event ->
            setFollowsDropdown(false)
        }

        setPageTitle("Profile")
        document.addEventListener("click", hideDropdown)
        cleanup {
            document.removeEventListener("click", hideDropdown)
        }
    }

    useEffect(location.pathname, params["userId"]) {
        val onHashChange = { _: Event ->
            val hash = window.location.hash.substring(1)
            val tabContext = TabContext(params["userId"]?.toIntOrNull())
            val newState = ProfileTab.entries.firstOrNull { hash == it.tabText.lowercase() && it.condition(userData, tabContext, userDetail) } ?: tabState
            setTabState(newState)
        }

        window.addEventListener("hashchange", onHashChange)
        loadState()

        cleanup {
            window.removeEventListener("hashchange", onHashChange)
        }
    }

    val loggedInLocal = userData?.userId
    modal {
        ref = modalRef
    }

    modalContext.Provider {
        attrs.value = modalRef

        div("row") {
            div("col-md-4 mb-3") {
                div("card user-info" + if (userDetail?.patreon?.supporting == true) " border border-3 border-patreon" else "") {
                    div("card-body") {
                        div("d-flex align-items-center mb-2") {
                            img("Profile Image", userDetail?.avatar, classes = "rounded-circle me-3") {
                                attrs.width = "50"
                                attrs.height = "50"
                            }
                            div("d-inline") {
                                h4("mb-1") {
                                    +(userDetail?.name ?: "")
                                }
                                p("text-muted mb-1") {
                                    +userTitles(userDetail).joinToString(", ")
                                }
                            }
                        }
                        div("mb-3") {
                            followData?.followers?.let {
                                span {
                                    a(if (it > 0) "#" else null) {
                                        attrs.onClickFunction = { e ->
                                            e.preventDefault()
                                            if (it > 0) {
                                                showFollows("Followers", following = userDetail?.id)
                                            }
                                        }

                                        +"Followed by "
                                        b("text-warning") { +"$it" }
                                        +(" user" + if (it != 1) "s" else "")
                                    }
                                }
                            }
                            followData?.follows?.let {
                                br { }
                                span {
                                    a(if (it > 0) "#" else null) {
                                        attrs.onClickFunction = { e ->
                                            e.preventDefault()
                                            if (it > 0) {
                                                showFollows("Follows", followedBy = userDetail?.id)
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
                                userDetail?.playlistUrl?.let { url ->
                                    div("btn-group") {
                                        a(url, "_blank", "btn btn-secondary") {
                                            attrs.attributes["download"] = ""
                                            i("fas fa-download") { }
                                            +"Playlist"
                                        }
                                        a(
                                            "bsplaylist://playlist/$url/beatsaver-user-${userDetail.id}.bplist",
                                            classes = "btn btn-primary"
                                        ) {
                                            attrs.attributes["aria-label"] = "One-Click"
                                            i("fas fa-cloud-download-alt m-0") { }
                                        }
                                    }
                                }
                                if (loggedInLocal != null && loggedInLocal != userDetail?.id) {
                                    followData?.let { fd ->
                                        div("btn-group") {
                                            val btnClasses = "btn btn-" + if (fd.following) "secondary" else "primary"
                                            button(classes = btnClasses) {
                                                attrs.disabled = loading
                                                attrs.onClickFunction = { e ->
                                                    e.preventDefault()
                                                    setFollowStatus(!fd.following, !fd.following, !fd.following, !fd.following)
                                                }

                                                if (fd.following) {
                                                    i("fas fa-user-minus") { }
                                                    +"Unfollow"
                                                } else {
                                                    i("fas fa-user-plus") { }
                                                    +"Follow"
                                                }
                                            }
                                            div("btn-group m-0") {
                                                button(classes = "dropdown-toggle $btnClasses") {
                                                    attrs.onClickFunction = {
                                                        it.stopPropagation()
                                                        setFollowsDropdown(!followsDropdown)
                                                    }
                                                }
                                                div("dropdown-menu mt-4" + if (followsDropdown) " show" else "") {
                                                    label("dropdown-item") {
                                                        attrs.htmlFor = "follow-uploads"
                                                        attrs.role = "button"
                                                        attrs.onClickFunction = {
                                                            it.stopPropagation()
                                                        }
                                                        input(InputType.checkBox, classes = "form-check-input me-2") {
                                                            key = "follow-uploads-${fd.upload}"
                                                            attrs.id = "follow-uploads"
                                                            attrs.disabled = loading
                                                            attrs.checked = fd.upload
                                                            attrs.onChangeFunction = { ev ->
                                                                val newUpload = (ev.target as HTMLInputElement).checked
                                                                setFollowStatus(fd.following || newUpload || fd.curation || fd.collab, newUpload, fd.curation, fd.collab)
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
                                                            key = "follow-curations-${fd.curation}"
                                                            attrs.id = "follow-curations"
                                                            attrs.disabled = loading
                                                            attrs.checked = fd.curation
                                                            attrs.onChangeFunction = { ev ->
                                                                val newCuration = (ev.target as HTMLInputElement).checked
                                                                setFollowStatus(fd.following || fd.upload || newCuration || fd.collab, fd.upload, newCuration, fd.collab)
                                                            }
                                                        }
                                                        +"Curations"
                                                    }
                                                    label("dropdown-item") {
                                                        attrs.htmlFor = "follow-collabs"
                                                        attrs.role = "button"
                                                        attrs.onClickFunction = {
                                                            it.stopPropagation()
                                                        }
                                                        input(InputType.checkBox, classes = "form-check-input me-2") {
                                                            key = "follow-collabs-${fd.collab}"
                                                            attrs.id = "follow-collabs"
                                                            attrs.disabled = loading
                                                            attrs.checked = fd.collab
                                                            attrs.onChangeFunction = { ev ->
                                                                val newCollab = (ev.target as HTMLInputElement).checked
                                                                setFollowStatus(fd.following || fd.upload || fd.curation || newCollab, fd.upload, fd.curation, newCollab)
                                                            }
                                                        }
                                                        +"Collabs"
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (!userData.suspended && !userData.admin && loggedInLocal != userDetail?.id && userDetail?.id != null) {
                                        div("btn-group") {
                                            button(classes = "btn btn-danger") {
                                                attrs.disabled = loading
                                                attrs.attributes["aria-label"] = "Report"
                                                attrs.onClickFunction = { e ->
                                                    e.preventDefault()
                                                    modalRef.current?.showDialog(
                                                        ModalData(
                                                            "Report user",
                                                            bodyCallback = {
                                                                p {
                                                                    +"Why are you reporting this user? Please give as much detail as possible why you feel this user has violated our TOS:"
                                                                }
                                                                textarea(classes = "form-control") {
                                                                    ref = reasonRef
                                                                }
                                                                recaptcha(captchaRef)
                                                            },
                                                            buttons = listOf(
                                                                ModalButton("Report", "danger") { report(userDetail.id) },
                                                                ModalButton("Cancel")
                                                            )
                                                        )
                                                    )
                                                }

                                                i("fas fa-flag m-0") { }
                                            }
                                        }
                                    }
                                }
                            }
                            if (userData?.admin == true || userData?.curator == true) {
                                div("mt-2") {
                                    div("btn-group") {
                                        if (userData.admin) {
                                            routeLink("/modlog?user=${userDetail?.name}", className = "btn btn-secondary") {
                                                i("fas fa-scroll") { }
                                                +"Mod Log"
                                            }
                                        }
                                        routeLink("/modreview?user=${userDetail?.name}", className = "btn btn-secondary") {
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
                        if (userDetail?.suspendedAt != null) {
                            span("text-danger") {
                                +"This user has been suspended."
                            }
                        } else {
                            span {
                                textToContent((userDetail?.description?.take(500) ?: ""))
                            }
                        }
                        userDetail?.stats?.let {
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
        val userId = params["userId"]?.toIntOrNull()
        ul("nav nav-minimal mb-3") {
            val tabContext = TabContext(userId)
            ProfileTab.entries.forEach { tab ->
                if (!tab.condition(userData, tabContext, userDetail)) return@forEach

                li("nav-item") {
                    a("#", classes = "nav-link" + if (tabState == tab) " active" else "") {
                        key = tab.tabText
                        attrs.onClickFunction = {
                            it.preventDefault()

                            val userPart = if (userId != null) "/$userId" else ""
                            history.push("/profile$userPart#${tab.tabText.lowercase()}")

                            tab.onSelected(tabContext)
                            setTabState(tab)
                        }

                        span {
                            +tab.tabText
                        }
                    }
                }
            }

            if (setOf(ProfileTab.PUBLISHED, ProfileTab.CURATED).contains(tabState)) {
                li("nav-item right") {
                    sort {
                        attrs.target = SortOrderTarget.UserMap
                        attrs.cb = {
                            setSortOrder(it)
                        }
                        attrs.default = sortOrder
                        attrs.dark = true
                    }
                }
            }
        }
        if (userDetail != null) {
            playlistTable {
                attrs.userId = userDetail.id
                attrs.visible = tabState == ProfileTab.PLAYLISTS
            }
            if (startup) {
                beatmapTable {
                    attrs.key = "maps-$tabState"
                    attrs.user = userId ?: loggedInLocal ?: 0
                    attrs.wip = tabState == ProfileTab.UNPUBLISHED
                    attrs.curated = tabState == ProfileTab.CURATED
                    attrs.visible = setOf(ProfileTab.UNPUBLISHED, ProfileTab.PUBLISHED, ProfileTab.CURATED).contains(tabState)
                    attrs.fallbackOrder = sortOrder
                }
            }
        }

        if (tabState == ProfileTab.REVIEWS) {
            reviewTable {
                attrs.userDetail = userDetail
                // There may be collaborators passed here, however they are not passed here as they are not required
                // And would just result in the purposeless loading of additional data
            }
        }

        if (tabState == ProfileTab.ACCOUNT && userDetail != null) {
            if (userId == null) {
                accountTab {
                    attrs.key = "account"
                    attrs.userDetail = userDetail
                    attrs.onUpdate = { loadState() }
                }
            } else {
                adminAccount {
                    attrs.userDetail = userDetail
                    attrs.onUpdate = { loadState() }
                }
            }
        }
    }
}
