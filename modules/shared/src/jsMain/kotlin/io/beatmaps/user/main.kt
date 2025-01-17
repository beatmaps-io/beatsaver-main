package io.beatmaps.user

import external.Axios
import external.axiosGet
import external.generateConfig
import external.routeLink
import io.beatmaps.Config
import io.beatmaps.History
import io.beatmaps.UserData
import io.beatmaps.admin.admin
import io.beatmaps.api.IssueCreationRequest
import io.beatmaps.api.UserDetail
import io.beatmaps.api.UserFollowData
import io.beatmaps.api.UserFollowRequest
import io.beatmaps.captcha.ICaptchaHandler
import io.beatmaps.common.SearchOrder
import io.beatmaps.common.SortOrderTarget
import io.beatmaps.common.api.UserReportData
import io.beatmaps.common.json
import io.beatmaps.globalContext
import io.beatmaps.index.beatmapTable
import io.beatmaps.issues.reportModal
import io.beatmaps.playlist.playlists
import io.beatmaps.setPageTitle
import io.beatmaps.shared.ModalButton
import io.beatmaps.shared.ModalCallbacks
import io.beatmaps.shared.ModalData
import io.beatmaps.shared.loadingElem
import io.beatmaps.shared.modal
import io.beatmaps.shared.modalContext
import io.beatmaps.shared.review.reviewTable
import io.beatmaps.shared.search.sort
import io.beatmaps.util.fcmemo
import io.beatmaps.util.orCatch
import io.beatmaps.util.textToContent
import js.objects.jso
import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.browser.window
import org.w3c.dom.events.Event
import org.w3c.dom.get
import org.w3c.dom.set
import react.Props
import react.Suspense
import react.dom.aria.AriaRole
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h4
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import react.dom.html.ReactHTML.ul
import react.router.useLocation
import react.router.useNavigate
import react.router.useParams
import react.useContext
import react.useEffectOnceWithCleanup
import react.useEffectWithCleanup
import react.useRef
import react.useState
import web.cssom.ClassName
import web.cssom.pct
import web.html.HTMLTextAreaElement
import web.html.InputType
import web.window.WindowTarget
import kotlin.js.Promise

data class TabContext(val userId: Int?)

enum class ProfileTab(val tabText: String, val condition: (UserData?, TabContext, UserDetail?) -> Boolean = { _, _, _ -> true }, val bootCondition: () -> Boolean = { false }, val onSelected: (TabContext) -> Unit = {}) {
    PUBLISHED("Published", bootCondition = { (localStorage["profile.showwip"] == "false") }, onSelected = { if (it.userId == null) { localStorage["profile.showwip"] = "false" } }),
    UNPUBLISHED("Unpublished", condition = { _, c, _ -> (c.userId == null) }, bootCondition = { (localStorage["profile.showwip"] == "true") }, onSelected = { if (it.userId == null) { localStorage["profile.showwip"] = "true" } }),
    PLAYLISTS("Playlists"),
    CURATED("Curations", condition = { _, _, it -> (it?.curatorTab == true) }),
    REVIEWS("Reviews"),
    ACCOUNT("Account", condition = { it, c, _ -> (it?.admin == true || c.userId == null) })
}

val profilePage = fcmemo<Props>("profilePage") { _ ->
    val captchaRef = useRef<ICaptchaHandler>()
    val reasonRef = useRef<HTMLTextAreaElement>()
    val modalRef = useRef<ModalCallbacks>()
    val userData = useContext(globalContext)

    val (startup, setStartup) = useState(false)
    val (userDetail, setUserDetail) = useState<UserDetail>()
    val (tabState, setTabState) = useState<ProfileTab>()
    val (followData, setFollowData) = useState<UserFollowData>()
    val (followsDropdown, setFollowsDropdown) = useState(false)
    val (loading, setLoading) = useState(false)
    val errorRef = useRef<List<String>>()
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
        modalRef.current?.hide?.invoke()
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
        modalRef.current?.showDialog?.invoke(
            ModalData(
                title,
                bodyCallback = {
                    Suspense {
                        user.followList {
                            attrs.scrollParent = it
                            attrs.following = following
                            attrs.followedBy = followedBy
                        }
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

    fun report(userId: Int) =
        captchaRef.current?.let { cc ->
            cc.execute()?.then { captcha ->
                val reason = reasonRef.current?.value?.trim() ?: ""
                Axios.post<String>(
                    "${Config.apibase}/issues/create",
                    IssueCreationRequest(captcha, reason, UserReportData(userId)),
                    generateConfig<IssueCreationRequest, String>(validStatus = arrayOf(201))
                ).then {
                    history.push("/issues/${it.data}")
                    true
                }
            }?.orCatch {
                errorRef.current = listOfNotNull(it.message)
                false
            }
        } ?: Promise.resolve(false)

    useEffectOnceWithCleanup {
        val hideDropdown = { _: Event ->
            setFollowsDropdown(false)
        }

        setPageTitle("Profile")
        document.addEventListener("click", hideDropdown)
        onCleanup {
            document.removeEventListener("click", hideDropdown)
        }
    }

    useEffectWithCleanup(location.pathname, params["userId"]) {
        val onHashChange = { _: Event ->
            val hash = window.location.hash.substring(1)
            val tabContext = TabContext(params["userId"]?.toIntOrNull())
            val newState = ProfileTab.entries.firstOrNull { hash == it.tabText.lowercase() && it.condition(userData, tabContext, userDetail) } ?: tabState
            setTabState(newState)
        }

        window.addEventListener("hashchange", onHashChange)
        loadState()

        onCleanup {
            window.removeEventListener("hashchange", onHashChange)
        }
    }

    val loggedInLocal = userData?.userId
    modal {
        attrs.callbacks = modalRef
    }

    modalContext.Provider {
        attrs.value = modalRef

        div {
            attrs.className = ClassName("row")
            div {
                attrs.className = ClassName("col-md-4 mb-3")
                div {
                    attrs.className = ClassName("card user-info" + if (userDetail?.patreon?.supporting == true) " border border-3 border-patreon" else "")
                    div {
                        attrs.className = ClassName("card-body")
                        div {
                            attrs.className = ClassName("d-flex align-items-center mb-2")
                            img {
                                attrs.alt = "Profile Image"
                                attrs.src = userDetail?.avatar
                                attrs.className = ClassName("rounded-circle me-3")
                                attrs.width = 50.0
                                attrs.height = 50.0
                            }
                            div {
                                attrs.className = ClassName("d-inline")
                                h4 {
                                    attrs.className = ClassName("mb-1")
                                    +(userDetail?.name ?: "")
                                }
                                p {
                                    attrs.className = ClassName("text-muted mb-1")
                                    +userTitles(userDetail).joinToString(", ")
                                }
                            }
                        }
                        div {
                            attrs.className = ClassName("mb-3")
                            followData?.followers?.let {
                                span {
                                    a {
                                        attrs.href = if (it > 0) "#" else null
                                        attrs.onClick = { e ->
                                            e.preventDefault()
                                            if (it > 0) {
                                                showFollows("Followers", following = userDetail?.id)
                                            }
                                        }

                                        +"Followed by "
                                        b {
                                            attrs.className = ClassName("text-warning")
                                            +"$it"
                                        }
                                        +(" user" + if (it != 1) "s" else "")
                                    }
                                }
                            }
                            followData?.follows?.let {
                                br { }
                                span {
                                    a {
                                        attrs.href = if (it > 0) "#" else null
                                        attrs.onClick = { e ->
                                            e.preventDefault()
                                            if (it > 0) {
                                                showFollows("Follows", followedBy = userDetail?.id)
                                            }
                                        }

                                        +"Following "
                                        b {
                                            attrs.className = ClassName("text-warning")
                                            +"$it"
                                        }
                                        +(" user" + if (it != 1) "s" else "")
                                    }
                                }
                            }
                        }
                        div {
                            attrs.className = ClassName("button-wrap")
                            userDetail?.playlistUrl?.let { url ->
                                div {
                                    attrs.className = ClassName("btn-group")
                                    a {
                                        attrs.href = url
                                        attrs.target = WindowTarget._blank
                                        attrs.className = ClassName("btn btn-secondary")
                                        attrs.id = "dl-playlist"
                                        attrs.download = ""
                                        i {
                                            attrs.className = ClassName("fas fa-download")
                                        }
                                        +"Playlist"
                                    }
                                    a {
                                        attrs.href = "bsplaylist://playlist/$url/beatsaver-user-${userDetail.id}.bplist"
                                        attrs.className = ClassName("btn btn-primary")
                                        attrs.id = "oc-playlist"
                                        attrs.ariaLabel = "One-Click"
                                        i {
                                            attrs.className = ClassName("fas fa-cloud-download-alt m-0")
                                        }
                                    }
                                }
                            }
                            if (loggedInLocal != null && loggedInLocal != userDetail?.id) {
                                followData?.let { fd ->
                                    div {
                                        attrs.className = ClassName("btn-group")
                                        val btnClasses = ClassName("btn btn-" + if (fd.following) "secondary" else "primary")
                                        button {
                                            attrs.className = btnClasses
                                            attrs.id = "follow"
                                            attrs.disabled = loading
                                            attrs.onClick = { e ->
                                                e.preventDefault()
                                                setFollowStatus(!fd.following, !fd.following, !fd.following, !fd.following)
                                            }

                                            if (fd.following) {
                                                i {
                                                    attrs.className = ClassName("fas fa-user-minus")
                                                }
                                                +"Unfollow"
                                            } else {
                                                i {
                                                    attrs.className = ClassName("fas fa-user-plus")
                                                }
                                                +"Follow"
                                            }
                                        }
                                        div {
                                            attrs.className = ClassName("btn-group m-0")
                                            button {
                                                attrs.className = ClassName("dropdown-toggle $btnClasses")
                                                attrs.id = "follow-dd"
                                                attrs.onClick = {
                                                    it.stopPropagation()
                                                    setFollowsDropdown(!followsDropdown)
                                                }
                                            }
                                            div {
                                                attrs.className = ClassName("dropdown-menu mt-4" + if (followsDropdown) " show" else "")
                                                label {
                                                    attrs.className = ClassName("dropdown-item")
                                                    attrs.htmlFor = "follow-uploads"
                                                    attrs.role = AriaRole.button
                                                    attrs.onClick = {
                                                        it.stopPropagation()
                                                    }
                                                    input {
                                                        key = "follow-uploads-${fd.upload}"
                                                        attrs.type = InputType.checkbox
                                                        attrs.className = ClassName("form-check-input me-2")
                                                        attrs.id = "follow-uploads"
                                                        attrs.disabled = loading
                                                        attrs.checked = fd.upload
                                                        attrs.onChange = { ev ->
                                                            val newUpload = ev.target.checked
                                                            setFollowStatus(fd.following || newUpload || fd.curation || fd.collab, newUpload, fd.curation, fd.collab)
                                                        }
                                                    }
                                                    +"Uploads"
                                                }
                                                label {
                                                    attrs.className = ClassName("dropdown-item")
                                                    attrs.htmlFor = "follow-curations"
                                                    attrs.role = AriaRole.button
                                                    attrs.onClick = {
                                                        it.stopPropagation()
                                                    }
                                                    input {
                                                        key = "follow-curations-${fd.curation}"
                                                        attrs.type = InputType.checkbox
                                                        attrs.className = ClassName("form-check-input me-2")
                                                        attrs.id = "follow-curations"
                                                        attrs.disabled = loading
                                                        attrs.checked = fd.curation
                                                        attrs.onChange = { ev ->
                                                            val newCuration = ev.target.checked
                                                            setFollowStatus(fd.following || fd.upload || newCuration || fd.collab, fd.upload, newCuration, fd.collab)
                                                        }
                                                    }
                                                    +"Curations"
                                                }
                                                label {
                                                    attrs.className = ClassName("dropdown-item")
                                                    attrs.htmlFor = "follow-collabs"
                                                    attrs.role = AriaRole.button
                                                    attrs.onClick = {
                                                        it.stopPropagation()
                                                    }
                                                    input {
                                                        key = "follow-collabs-${fd.collab}"
                                                        attrs.type = InputType.checkbox
                                                        attrs.className = ClassName("form-check-input me-2")
                                                        attrs.id = "follow-collabs"
                                                        attrs.disabled = loading
                                                        attrs.checked = fd.collab
                                                        attrs.onChange = { ev ->
                                                            val newCollab = ev.target.checked
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
                                    div {
                                        attrs.className = ClassName("btn-group")
                                        button {
                                            attrs.className = ClassName("btn btn-danger")
                                            attrs.id = "report"
                                            attrs.disabled = loading
                                            attrs.ariaLabel = "Report"
                                            attrs.onClick = { e ->
                                                e.preventDefault()
                                                modalRef.current?.showDialog?.invoke(
                                                    ModalData(
                                                        "Report user",
                                                        bodyCallback = {
                                                            reportModal {
                                                                attrs.content = false
                                                                attrs.subject = "user"
                                                                attrs.reasonRef = reasonRef
                                                                attrs.captchaRef = captchaRef
                                                                attrs.errorsRef = errorRef
                                                            }
                                                        },
                                                        buttons = listOf(
                                                            ModalButton("Report", "danger") { report(userDetail.id) },
                                                            ModalButton("Cancel")
                                                        )
                                                    )
                                                )
                                            }

                                            i {
                                                attrs.className = ClassName("fas fa-flag m-0")
                                            }
                                        }
                                    }
                                }
                            }
                            if (userData?.admin == true || userData?.curator == true) {
                                div {
                                    attrs.className = ClassName("btn-group")
                                    if (userData.admin) {
                                        routeLink("/modlog?user=${userDetail?.name}", className = "btn btn-secondary") {
                                            i {
                                                attrs.className = ClassName("fas fa-scroll")
                                            }
                                            +"Mod Log"
                                        }
                                    }
                                    routeLink("/modreview?user=${userDetail?.name}", className = "btn btn-secondary") {
                                        i {
                                            attrs.className = ClassName("fas fa-heartbeat")
                                        }
                                        +"Review Log"
                                    }
                                }
                            }
                        }
                    }
                }
            }
            div {
                attrs.className = ClassName("col-md-8 mb-3 position-relative")
                div {
                    attrs.className = ClassName("card user-info")
                    div {
                        attrs.className = ClassName("card-body")
                        if (userDetail?.suspendedAt != null) {
                            span {
                                attrs.className = ClassName("text-danger")
                                +"This user has been suspended."
                            }
                        } else {
                            span {
                                textToContent((userDetail?.description?.take(500) ?: ""))
                            }
                        }
                        userDetail?.stats?.let {
                            if (it.totalMaps != 0) {
                                table {
                                    attrs.className = ClassName("table table-dark")
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
                                                    div {
                                                        attrs.className = ClassName("difficulty-spread mb-1")
                                                        div {
                                                            attrs.className = ClassName("badge-green")
                                                            attrs.style = jso {
                                                                flex = ds.easy.pct
                                                            }
                                                            attrs.title = "${ds.easy}"
                                                        }
                                                        div {
                                                            attrs.className = ClassName("badge-blue")
                                                            attrs.style = jso {
                                                                flex = ds.normal.pct
                                                            }
                                                            attrs.title = "${ds.normal}"
                                                        }
                                                        div {
                                                            attrs.className = ClassName("badge-hard")
                                                            attrs.style = jso {
                                                                flex = ds.hard.pct
                                                            }
                                                            attrs.title = "${ds.hard}"
                                                        }
                                                        div {
                                                            attrs.className = ClassName("badge-expert")
                                                            attrs.style = jso {
                                                                flex = ds.expert.pct
                                                            }
                                                            attrs.title = "${ds.expert}"
                                                        }
                                                        div {
                                                            attrs.className = ClassName("badge-purple")
                                                            attrs.style = jso {
                                                                flex = ds.expertPlus.pct
                                                            }
                                                            attrs.title = "${ds.expertPlus}"
                                                        }
                                                    }
                                                    div {
                                                        attrs.className = ClassName("legend")
                                                        span {
                                                            attrs.className = ClassName("legend-green")
                                                            +"Easy"
                                                        }
                                                        span {
                                                            attrs.className = ClassName("legend-blue")
                                                            +"Normal"
                                                        }
                                                        span {
                                                            attrs.className = ClassName("legend-hard")
                                                            +"Hard"
                                                        }
                                                        span {
                                                            attrs.className = ClassName("legend-expert")
                                                            +"Expert"
                                                        }
                                                        span {
                                                            attrs.className = ClassName("legend-purple")
                                                            +"Expert+"
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
        }
        val userId = params["userId"]?.toIntOrNull()
        ul {
            attrs.className = ClassName("nav nav-minimal mb-3")
            val tabContext = TabContext(userId)
            ProfileTab.entries.forEach { tab ->
                if (!tab.condition(userData, tabContext, userDetail)) return@forEach

                li {
                    attrs.className = ClassName("nav-item")
                    a {
                        key = tab.tabText
                        attrs.href = "#"
                        attrs.className = ClassName("nav-link" + if (tabState == tab) " active" else "")
                        attrs.onClick = {
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
                li {
                    attrs.className = ClassName("nav-item right")
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
            Suspense {
                playlists.table {
                    attrs.userId = userDetail.id
                    attrs.visible = tabState == ProfileTab.PLAYLISTS
                }
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
                Suspense {
                    attrs.fallback = loadingElem
                    admin.adminAccount {
                        attrs.userDetail = userDetail
                        attrs.onUpdate = { loadState() }
                    }
                }
            }
        }
    }
}
