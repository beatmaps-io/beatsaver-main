package io.beatmaps.admin.issues

import external.CancelTokenSource
import external.TimeAgo
import external.axiosGet
import external.routeLink
import io.beatmaps.Config
import io.beatmaps.History
import io.beatmaps.api.GenericSearchResponse
import io.beatmaps.api.HydratedMapReportData
import io.beatmaps.api.HydratedPlaylistReportData
import io.beatmaps.api.HydratedReviewReportData
import io.beatmaps.api.HydratedUserReportData
import io.beatmaps.api.IssueDetail
import io.beatmaps.common.api.EIssueType
import io.beatmaps.globalContext
import io.beatmaps.setPageTitle
import io.beatmaps.shared.InfiniteScrollElementRenderer
import io.beatmaps.shared.form.toggle
import io.beatmaps.shared.generateInfiniteScrollComponent
import io.beatmaps.util.fcmemo
import io.beatmaps.util.useDidUpdateEffect
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.form
import react.dom.html.ReactHTML.option
import react.dom.html.ReactHTML.select
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import react.router.useLocation
import react.router.useNavigate
import react.use
import react.useEffect
import react.useEffectOnce
import react.useMemo
import react.useRef
import react.useState
import web.cssom.ClassName
import web.html.ButtonType
import web.html.HTMLElement
import web.html.HTMLInputElement
import web.url.URLSearchParams
import kotlin.js.Promise

val issueList = fcmemo<Props>("issueList") {
    val resultsTable = useRef<HTMLElement>()

    val openRef = useRef<HTMLInputElement>()

    val userData = use(globalContext)
    val history = History(useNavigate())
    val location = useLocation()

    val (openLocal, typeLocal) = URLSearchParams(location.search).let { u ->
        Pair(u["open"].toBoolean(), EIssueType.fromName(u["type"] ?: ""))
    }

    val (isOpen, setIsOpen) = useState(openLocal)
    val (type, setType) = useState(typeLocal)
    val (newType, setNewType) = useState(typeLocal)
    val resetRef = useRef<() -> Unit>()
    val loadPageRef = useRef<(Int, CancelTokenSource) -> Promise<GenericSearchResponse<IssueDetail>?>>()

    useEffectOnce {
        setPageTitle("Issues")

        if (userData?.curator != true) {
            history.push("/")
        }
    }

    useEffect(location) {
        openRef.current?.checked = openLocal
        setNewType(typeLocal)

        setIsOpen(openLocal)
        setType(typeLocal)
    }

    useDidUpdateEffect(isOpen, type) {
        resetRef.current?.invoke()
    }

    fun urlExtension(): String {
        val params = listOfNotNull(
            // Fallback to isOpen to allow this to be called before first render
            (openRef.current?.checked ?: isOpen).let { if (it) "open=$it" else null },
            newType?.let { "type=$it" }
        )

        return if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
    }

    loadPageRef.current = { toLoad: Int, token: CancelTokenSource ->
        axiosGet<List<IssueDetail>>("${Config.apibase}/issues/list/$toLoad" + urlExtension(), token.token).then({
            return@then GenericSearchResponse.from(it.data)
        })
    }

    fun updateHistory() {
        val ext = urlExtension()
        if (location.search != ext) {
            history.push("/issues$ext")
        }
    }

    val renderer = useMemo {
        InfiniteScrollElementRenderer<IssueDetail> { it ->
            tr {
                it?.let {
                    td {
                        routeLink(it.creator.profileLink()) {
                            +it.creator.name
                        }
                    }
                    td {
                        routeLink("/issues/${it.id}") {
                            +it.type.name
                        }
                    }
                    td {
                        TimeAgo.default {
                            date = it.createdAt.toString()
                        }
                    }
                    td {
                        if (it.closedAt == null) {
                            +"OPEN"
                        } else {
                            TimeAgo.default {
                                date = it.closedAt.toString()
                            }
                        }
                    }
                    td {
                        when (val data = it.data) {
                            is HydratedMapReportData -> {
                                routeLink(data.map.link()) {
                                    +data.map.name
                                }
                            }

                            is HydratedUserReportData -> {
                                routeLink(data.user.profileLink()) {
                                    +data.user.name
                                }
                            }

                            is HydratedPlaylistReportData -> {
                                routeLink(data.playlist.link()) {
                                    +data.playlist.name
                                }
                            }

                            is HydratedReviewReportData -> {
                                data.review.map?.let { m ->
                                    routeLink(m.link()) {
                                        +m.name
                                    }
                                }
                            }

                            null -> {
                                +"[DELETED]"
                            }
                        }
                    }
                } ?: run {
                    td {
                        colSpan = 5
                    }
                }
            }
        }
    }

    form {
        table {
            className = ClassName("table table-dark table-striped modlog")
            thead {
                tr {
                    th { +"Creator" }
                    th { +"Type" }
                    th { +"Opened" }
                    th { +"Closed" }
                    th { +"Item" }
                }
                tr {
                    td { }
                    td {
                        select {
                            className = ClassName("form-select")
                            ariaLabel = "Type"
                            value = newType?.name ?: ""
                            onChange = {
                                setNewType(EIssueType.fromName(it.currentTarget.value))
                            }

                            EIssueType.entries.filter { userData?.admin == true || it.curatorAllowed }.forEach {
                                option {
                                    value = it.name
                                    +it.toString()
                                }
                            }
                            option {
                                value = ""
                                +"All"
                            }
                        }
                    }
                    td { }
                    td {
                        toggle {
                            id = "openOnly"
                            toggleRef = openRef
                            text = "Open only"
                            default = isOpen
                            className = "mb-2"
                        }
                    }
                    td {
                        button {
                            className = ClassName("btn btn-primary")
                            this.type = ButtonType.submit
                            onClick = {
                                it.preventDefault()
                                updateHistory()
                            }

                            +"Filter"
                        }
                    }
                }
            }
            tbody {
                ref = resultsTable
                key = "issuesTable"

                issuesInfiniteScroll {
                    this.resetRef = resetRef
                    rowHeight = 47.5
                    itemsPerPage = 30
                    container = resultsTable
                    loadPage = loadPageRef
                    renderElement = renderer
                }
            }
        }
    }
}

val issuesInfiniteScroll = generateInfiniteScrollComponent(IssueDetail::class)
