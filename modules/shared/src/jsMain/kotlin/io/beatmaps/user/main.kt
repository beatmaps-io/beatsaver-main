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
import io.beatmaps.common.api.EIssueType
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
import io.beatmaps.util.get
import io.beatmaps.util.orCatch
import io.beatmaps.util.set
import io.beatmaps.util.textToContent
import js.objects.jso
import react.Props
import react.Suspense
import react.dom.aria.AriaRole
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h4
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
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
import react.use
import react.useEffectOnceWithCleanup
import react.useEffectWithCleanup
import react.useRef
import react.useState
import web.cssom.ClassName
import web.cssom.number
import web.dom.document
import web.events.Event
import web.events.addEventListener
import web.events.removeEventListener
import web.history.HashChangeEvent
import web.html.HTMLTextAreaElement
import web.html.InputType
import web.storage.localStorage
import web.window.WindowTarget
import web.window.window
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
    val userData = use(globalContext)

    val (startup, setStartup) = useState(false)
    val (userDetail, setUserDetail) = useState<UserDetail>()
    val (tabState, setTabState) = useState<ProfileTab>()
    val (followData, setFollowData) = useState<UserFollowData>()
    val (followsDropdown, setFollowsDropdown) = useState(false)
    val (loading, setLoading) = useState(false)
    val errorRef = useRef<List<String>>()
    val publishedSortOrder = useState(SearchOrder.Relevance)
    val publishedAscending = useState(false)
    val curationsSortOrder = useState(SearchOrder.Curated)
    val curationsAscending = useState(false)
    val (sortOrder, setSortOrder) = if (tabState == ProfileTab.CURATED) curationsSortOrder else publishedSortOrder
    val (ascending, setAscending) = if (tabState == ProfileTab.CURATED) curationsAscending else publishedAscending

    val location = useLocation()
    val history = History(useNavigate())
    val params = useParams()

    fun defaultTab(tabContext: TabContext, user: UserDetail?) =
        if (ProfileTab.UNPUBLISHED.condition(userData, tabContext, user)) {
            ProfileTab.UNPUBLISHED
        } else {
            ProfileTab.PUBLISHED
        }

    fun firstTab(tabContext: TabContext, user: UserDetail?) =
        ProfileTab.entries.firstOrNull { it.bootCondition() && it.condition(userData, tabContext, user) }

    fun tabFromHash(user: UserDetail?): ProfileTab {
        val hash = window.location.hash.substring(1)
        val tabContext = TabContext(params["userId"]?.toIntOrNull())
        return ProfileTab.entries.firstOrNull {
            hash == it.tabText.lowercase() && it.condition(userData, tabContext, user)
        } ?: firstTab(tabContext, user) ?: defaultTab(tabContext, user)
    }

    fun setupTabState(user: UserDetail?) {
        setTabState(tabFromHash(user))
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
                            scrollParent = it
                            this.following = following
                            this.followedBy = followedBy
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
                    IssueCreationRequest(captcha, reason, userId, EIssueType.UserReport),
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
        document.addEventListener(Event.CLICK, hideDropdown)
        onCleanup {
            document.removeEventListener(Event.CLICK, hideDropdown)
        }
    }

    useEffectWithCleanup(location.pathname, params["userId"]) {
        val onHashChange = { _: Event ->
            setTabState(tabFromHash(userDetail))
        }

        window.addEventListener(HashChangeEvent.HASH_CHANGE, onHashChange)
        loadState()

        onCleanup {
            window.removeEventListener(HashChangeEvent.HASH_CHANGE, onHashChange)
        }
    }

    val loggedInLocal = userData?.userId
    modal {
        callbacks = modalRef
    }

    modalContext.Provider {
        value = modalRef

        div {
            className = ClassName("row")
            div {
                className = ClassName("col-md-4 mb-3")
                div {
                    className = ClassName("card user-info" + if (userDetail?.patreon?.supporting == true) " border border-3 border-patreon" else "")
                    div {
                        className = ClassName("card-body")
                        div {
                            className = ClassName("d-flex align-items-center mb-2")
                            img {
                                alt = "Profile Image"
                                src = userDetail?.avatar
                                className = ClassName("rounded-circle me-3")
                                width = 50.0
                                height = 50.0
                            }
                            div {
                                className = ClassName("d-inline")
                                h4 {
                                    className = ClassName("mb-1")
                                    +(userDetail?.name ?: "")
                                }
                                p {
                                    className = ClassName("text-muted mb-1")
                                    +userTitles(userDetail).joinToString(", ")
                                }
                            }
                        }
                        div {
                            className = ClassName("mb-3")
                            followData?.followers?.let {
                                span {
                                    a {
                                        href = if (it > 0) "#" else null
                                        onClick = { e ->
                                            e.preventDefault()
                                            if (it > 0) {
                                                showFollows("Followers", following = userDetail?.id)
                                            }
                                        }

                                        +"Followed by "
                                        b {
                                            className = ClassName("text-warning")
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
                                        href = if (it > 0) "#" else null
                                        onClick = { e ->
                                            e.preventDefault()
                                            if (it > 0) {
                                                showFollows("Follows", followedBy = userDetail?.id)
                                            }
                                        }

                                        +"Following "
                                        b {
                                            className = ClassName("text-warning")
                                            +"$it"
                                        }
                                        +(" user" + if (it != 1) "s" else "")
                                    }
                                }
                            }
                        }
                        div {
                            className = ClassName("button-wrap")
                            userDetail?.playlistUrl?.let { url ->
                                div {
                                    className = ClassName("btn-group")
                                    a {
                                        href = url
                                        target = WindowTarget._blank
                                        className = ClassName("btn btn-secondary")
                                        id = "dl-playlist"
                                        download = ""
                                        i {
                                            className = ClassName("fas fa-download")
                                        }
                                        +"Playlist"
                                    }
                                    a {
                                        href = "bsplaylist://playlist/$url/beatsaver-user-${userDetail.id}.bplist"
                                        className = ClassName("btn btn-primary")
                                        id = "oc-playlist"
                                        ariaLabel = "One-Click"
                                        i {
                                            className = ClassName("fas fa-cloud-download-alt m-0")
                                        }
                                    }
                                }
                            }
                            if (loggedInLocal != null && loggedInLocal != userDetail?.id) {
                                followData?.let { fd ->
                                    div {
                                        className = ClassName("btn-group")
                                        val btnClasses = ClassName("btn btn-" + if (fd.following) "secondary" else "primary")
                                        button {
                                            className = btnClasses
                                            id = "follow"
                                            disabled = loading
                                            onClick = { e ->
                                                e.preventDefault()
                                                setFollowStatus(!fd.following, !fd.following, !fd.following, !fd.following)
                                            }

                                            if (fd.following) {
                                                i {
                                                    className = ClassName("fas fa-user-minus")
                                                }
                                                +"Unfollow"
                                            } else {
                                                i {
                                                    className = ClassName("fas fa-user-plus")
                                                }
                                                +"Follow"
                                            }
                                        }
                                        div {
                                            className = ClassName("btn-group m-0")
                                            button {
                                                className = ClassName("dropdown-toggle $btnClasses")
                                                id = "follow-dd"
                                                onClick = {
                                                    it.stopPropagation()
                                                    setFollowsDropdown(!followsDropdown)
                                                }
                                            }
                                            div {
                                                className = ClassName("dropdown-menu mt-4" + if (followsDropdown) " show" else "")
                                                label {
                                                    className = ClassName("dropdown-item")
                                                    htmlFor = "follow-uploads"
                                                    role = AriaRole.button
                                                    onClick = {
                                                        it.stopPropagation()
                                                    }
                                                    input {
                                                        key = "follow-uploads-${fd.upload}"
                                                        type = InputType.checkbox
                                                        className = ClassName("form-check-input me-2")
                                                        id = "follow-uploads"
                                                        disabled = loading
                                                        checked = fd.upload
                                                        onChange = { ev ->
                                                            val newUpload = ev.target.checked
                                                            setFollowStatus(fd.following || newUpload || fd.curation || fd.collab, newUpload, fd.curation, fd.collab)
                                                        }
                                                    }
                                                    +"Uploads"
                                                }
                                                label {
                                                    className = ClassName("dropdown-item")
                                                    htmlFor = "follow-curations"
                                                    role = AriaRole.button
                                                    onClick = {
                                                        it.stopPropagation()
                                                    }
                                                    input {
                                                        key = "follow-curations-${fd.curation}"
                                                        type = InputType.checkbox
                                                        className = ClassName("form-check-input me-2")
                                                        id = "follow-curations"
                                                        disabled = loading
                                                        checked = fd.curation
                                                        onChange = { ev ->
                                                            val newCuration = ev.target.checked
                                                            setFollowStatus(fd.following || fd.upload || newCuration || fd.collab, fd.upload, newCuration, fd.collab)
                                                        }
                                                    }
                                                    +"Curations"
                                                }
                                                label {
                                                    className = ClassName("dropdown-item")
                                                    htmlFor = "follow-collabs"
                                                    role = AriaRole.button
                                                    onClick = {
                                                        it.stopPropagation()
                                                    }
                                                    input {
                                                        key = "follow-collabs-${fd.collab}"
                                                        type = InputType.checkbox
                                                        className = ClassName("form-check-input me-2")
                                                        id = "follow-collabs"
                                                        disabled = loading
                                                        checked = fd.collab
                                                        onChange = { ev ->
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
                                        className = ClassName("btn-group")
                                        button {
                                            className = ClassName("btn btn-danger")
                                            id = "report"
                                            disabled = loading
                                            ariaLabel = "Report"
                                            onClick = { e ->
                                                e.preventDefault()
                                                modalRef.current?.showDialog?.invoke(
                                                    ModalData(
                                                        "Report user",
                                                        bodyCallback = {
                                                            reportModal {
                                                                content = false
                                                                subject = "user"
                                                                this.reasonRef = reasonRef
                                                                this.captchaRef = captchaRef
                                                                errorsRef = errorRef
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
                                                className = ClassName("fas fa-flag m-0")
                                            }
                                        }
                                    }
                                }
                            }
                            if (userData?.admin == true || userData?.curator == true) {
                                div {
                                    className = ClassName("btn-group")
                                    if (userData.admin) {
                                        routeLink("/modlog?user=${userDetail?.name}", className = "btn btn-secondary") {
                                            i {
                                                className = ClassName("fas fa-scroll")
                                            }
                                            +"Mod Log"
                                        }
                                    }
                                    routeLink("/modreview?user=${userDetail?.name}", className = "btn btn-secondary") {
                                        i {
                                            className = ClassName("fas fa-heartbeat")
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
                className = ClassName("col-md-8 mb-3 position-relative")
                div {
                    className = ClassName("card user-info")
                    div {
                        className = ClassName("card-body")
                        if (userDetail?.suspendedAt != null) {
                            span {
                                className = ClassName("text-danger")
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
                                    className = ClassName("table table-dark")
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
                                                        className = ClassName("difficulty-spread mb-1")
                                                        div {
                                                            className = ClassName("badge-green")
                                                            style = jso {
                                                                flex = number(ds.easy.toDouble())
                                                            }
                                                            title = "${ds.easy}"
                                                        }
                                                        div {
                                                            className = ClassName("badge-blue")
                                                            style = jso {
                                                                flex = number(ds.normal.toDouble())
                                                            }
                                                            title = "${ds.normal}"
                                                        }
                                                        div {
                                                            className = ClassName("badge-hard")
                                                            style = jso {
                                                                flex = number(ds.hard.toDouble())
                                                            }
                                                            title = "${ds.hard}"
                                                        }
                                                        div {
                                                            className = ClassName("badge-expert")
                                                            style = jso {
                                                                flex = number(ds.expert.toDouble())
                                                            }
                                                            title = "${ds.expert}"
                                                        }
                                                        div {
                                                            className = ClassName("badge-purple")
                                                            style = jso {
                                                                flex = number(ds.expertPlus.toDouble())
                                                            }
                                                            title = "${ds.expertPlus}"
                                                        }
                                                    }
                                                    div {
                                                        className = ClassName("legend")
                                                        span {
                                                            className = ClassName("legend-green")
                                                            +"Easy"
                                                        }
                                                        span {
                                                            className = ClassName("legend-blue")
                                                            +"Normal"
                                                        }
                                                        span {
                                                            className = ClassName("legend-hard")
                                                            +"Hard"
                                                        }
                                                        span {
                                                            className = ClassName("legend-expert")
                                                            +"Expert"
                                                        }
                                                        span {
                                                            className = ClassName("legend-purple")
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
            className = ClassName("nav nav-minimal mb-3")
            val tabContext = TabContext(userId)
            ProfileTab.entries.forEach { tab ->
                if (!tab.condition(userData, tabContext, userDetail)) return@forEach

                li {
                    className = ClassName("nav-item")
                    a {
                        key = tab.tabText
                        href = "#"
                        className = ClassName("nav-link" + if (tabState == tab) " active" else "")
                        onClick = {
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
                    className = ClassName("nav-item right")
                    sort {
                        target = SortOrderTarget.UserMap
                        cb = { order, asc ->
                            setSortOrder(order)
                            setAscending(asc)
                        }
                        default = sortOrder
                        defaultAsc = ascending
                        dark = true
                    }
                }
            }
        }
        if (userDetail != null) {
            Suspense {
                playlists.table {
                    this.userId = userDetail.id
                    visible = tabState == ProfileTab.PLAYLISTS
                }
            }
            if (startup) {
                beatmapTable {
                    key = "maps-$tabState"
                    user = userId ?: loggedInLocal ?: 0
                    wip = tabState == ProfileTab.UNPUBLISHED
                    curated = tabState == ProfileTab.CURATED
                    visible = setOf(ProfileTab.UNPUBLISHED, ProfileTab.PUBLISHED, ProfileTab.CURATED).contains(tabState)
                    fallbackOrder = sortOrder
                    fallbackAsc = ascending
                }
            }
        }

        if (tabState == ProfileTab.REVIEWS) {
            reviewTable {
                this.userDetail = userDetail
                // There may be collaborators passed here, however they are not passed here as they are not required
                // And would just result in the purposeless loading of additional data
            }
        }

        if (tabState == ProfileTab.ACCOUNT && userDetail != null) {
            if (userId == null) {
                accountTab {
                    key = "account"
                    this.userDetail = userDetail
                    onUpdate = { loadState() }
                }
            } else {
                Suspense {
                    fallback = loadingElem
                    admin.adminAccount {
                        this.userDetail = userDetail
                        onUpdate = { loadState() }
                    }
                }
            }
        }
    }
}
