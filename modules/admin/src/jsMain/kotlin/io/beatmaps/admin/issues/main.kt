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
import io.beatmaps.util.useDidUpdateEffect
import kotlinx.html.ButtonType
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.url.URLSearchParams
import react.Props
import react.dom.button
import react.dom.form
import react.dom.option
import react.dom.select
import react.dom.table
import react.dom.tbody
import react.dom.td
import react.dom.th
import react.dom.thead
import react.dom.tr
import react.fc
import react.router.useLocation
import react.router.useNavigate
import react.useContext
import react.useEffect
import react.useEffectOnce
import react.useMemo
import react.useRef
import react.useState
import kotlin.js.Promise

val issueList = fc<Props>("issueList") {
    val resultsTable = useRef<HTMLElement>()

    val openRef = useRef<HTMLInputElement>()

    val userData = useContext(globalContext)
    val history = History(useNavigate())
    val location = useLocation()

    val (openLocal, typeLocal) = URLSearchParams(location.search).let { u ->
        Pair(u.get("open").toBoolean(), EIssueType.fromName(u.get("type") ?: ""))
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
                            attrs.date = it.createdAt.toString()
                        }
                    }
                    td {
                        if (it.closedAt == null) {
                            +"OPEN"
                        } else {
                            TimeAgo.default {
                                attrs.date = it.closedAt.toString()
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
                        attrs.colSpan = "5"
                    }
                }
            }
        }
    }

    form {
        table("table table-dark table-striped modlog") {
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
                        select("form-select") {
                            attrs.attributes["aria-label"] = "Type"
                            attrs.value = newType?.name ?: ""
                            attrs.onChangeFunction = {
                                val elem = it.currentTarget as HTMLSelectElement
                                setNewType(EIssueType.fromName(elem.value))
                            }

                            EIssueType.entries.filter { userData?.admin == true || it.curatorAllowed }.forEach {
                                option {
                                    attrs.value = it.name
                                    +it.toString()
                                }
                            }
                            option {
                                attrs.value = ""
                                +"All"
                            }
                        }
                    }
                    td { }
                    td {
                        toggle {
                            attrs.id = "openOnly"
                            attrs.toggleRef = openRef
                            attrs.text = "Open only"
                            attrs.default = isOpen
                            attrs.className = "mb-2"
                        }
                    }
                    td {
                        button(type = ButtonType.submit, classes = "btn btn-primary") {
                            attrs.onClickFunction = {
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
                    attrs.resetRef = resetRef
                    attrs.rowHeight = 47.5
                    attrs.itemsPerPage = 30
                    attrs.container = resultsTable
                    attrs.loadPage = loadPageRef
                    attrs.renderElement = renderer
                }
            }
        }
    }
}

val issuesInfiniteScroll = generateInfiniteScrollComponent(IssueDetail::class)
