package io.beatmaps.issues

import external.CancelTokenSource
import external.TimeAgo
import external.axiosGet
import external.routeLink
import io.beatmaps.Config
import io.beatmaps.History
import io.beatmaps.api.HydratedMapReportData
import io.beatmaps.api.HydratedPlaylistReportData
import io.beatmaps.api.HydratedReviewReportData
import io.beatmaps.api.HydratedUserReportData
import io.beatmaps.api.IssueDetail
import io.beatmaps.common.api.EIssueType
import io.beatmaps.globalContext
import io.beatmaps.setPageTitle
import io.beatmaps.shared.InfiniteScroll
import io.beatmaps.shared.InfiniteScrollElementRenderer
import io.beatmaps.shared.form.toggle
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
import react.useRef
import react.useState

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
    val (resultsKey, setResultsKey) = useState(Any())

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
        setResultsKey(Any())
    }

    fun urlExtension(): String {
        val params = listOfNotNull(
            openRef.current?.checked?.let { if (it) "open=$it" else null },
            newType?.let { "type=$it" }
        )

        return if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
    }

    val loadPage = { toLoad: Int, token: CancelTokenSource ->
        axiosGet<List<IssueDetail>>("${Config.apibase}/issues/list/$toLoad" + urlExtension(), token.token).then({
            return@then it.data
        })
    }

    fun updateHistory() {
        val ext = urlExtension()
        if (location.search != ext) {
            history.push("/issues$ext")
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

                            EIssueType.entries.filter { userData?.admin == true || it.curatorAllowed  }.forEach {
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

                child(IssuesInfiniteScroll::class) {
                    attrs.resultsKey = resultsKey
                    attrs.rowHeight = 47.5
                    attrs.itemsPerPage = 20
                    attrs.container = resultsTable
                    attrs.loadPage = loadPage
                    attrs.renderElement = InfiniteScrollElementRenderer {
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
                                            attrs.date = it.createdAt.toString()
                                        }
                                    }
                                }
                                td {
                                    when (it.data) {
                                        is HydratedMapReportData -> {
                                            routeLink(it.data.map.link()) {
                                                +it.data.map.name
                                            }
                                        }
                                        is HydratedUserReportData -> {
                                            routeLink(it.data.user.profileLink()) {
                                                +it.data.user.name
                                            }
                                        }
                                        is HydratedPlaylistReportData -> {
                                            routeLink(it.data.playlist.link()) {
                                                +it.data.playlist.name
                                            }
                                        }
                                        is HydratedReviewReportData -> {
                                            it.data.review.map?.let { m ->
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
            }
        }
    }
}

class IssuesInfiniteScroll : InfiniteScroll<IssueDetail>()
