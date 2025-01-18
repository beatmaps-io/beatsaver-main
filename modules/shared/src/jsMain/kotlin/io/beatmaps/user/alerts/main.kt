package io.beatmaps.user.alerts

import external.Axios
import external.CancelTokenSource
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.History
import io.beatmaps.api.AlertOptionsRequest
import io.beatmaps.api.AlertUpdateAll
import io.beatmaps.api.GenericSearchResponse
import io.beatmaps.api.UserAlert
import io.beatmaps.api.UserAlertStats
import io.beatmaps.common.api.EAlertType
import io.beatmaps.common.json
import io.beatmaps.setPageTitle
import io.beatmaps.shared.InfiniteScrollElementRenderer
import io.beatmaps.shared.generateInfiniteScrollComponent
import io.beatmaps.util.buildURL
import io.beatmaps.util.fcmemo
import io.beatmaps.util.includeIfNotNull
import io.beatmaps.util.updateAlertDisplay
import io.beatmaps.util.useDidUpdateEffect
import io.beatmaps.util.useObjectMemoize
import react.Props
import react.dom.aria.AriaRole
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.h6
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.span
import react.router.useLocation
import react.router.useNavigate
import react.useCallback
import react.useEffect
import react.useEffectOnce
import react.useMemo
import react.useRef
import react.useState
import web.cssom.ClassName
import web.html.HTMLElement
import web.html.InputType
import web.url.URLSearchParams
import kotlin.js.Promise

val alertsPage = fcmemo<Props>("alertsPage") {
    val location = useLocation()
    val params = URLSearchParams(location.search)

    val (newRead, newFilters) = params.get("read").toBoolean() to
        (params.get("type") ?: "").split(",").mapNotNull { EAlertType.fromLower(it) }

    val (read, setRead) = useState(newRead)
    val (filters, setFilters) = useState(newFilters)

    val (alertStats, setAlertStats) = useState<UserAlertStats?>(null)
    val resetRef = useRef<() -> Unit>()
    val loadPageRef = useRef<(Int, CancelTokenSource) -> Promise<GenericSearchResponse<UserAlert>?>>()
    val (hiddenAlerts, setHiddenAlerts) = useState(listOf<Int>())
    val (forceHide, setForceHide) = useState(false)
    val (loading, setLoading) = useState(false)

    val resultsColumn = useRef<HTMLElement>()
    val history = History(useNavigate())

    useEffectOnce {
        setPageTitle("Alerts")

        Axios.get<String>(
            "${Config.apibase}/alerts/stats",
            generateConfig<String, String>()
        ).then {
            // Decode is here so that 401 actually passes to error handler
            val data = json.decodeFromString<UserAlertStats>(it.data)
            updateAlertDisplay(data)

            setAlertStats(data)
        }.catch {
            if (it.asDynamic().response?.status == 401) {
                history.push("/login")
            }
        }
    }

    useEffect(location) {
        setRead(newRead)
        setFilters(newFilters)
    }

    useDidUpdateEffect(read, useObjectMemoize(filters)) {
        resetRef.current?.invoke()
        setHiddenAlerts(listOf())
        setForceHide(false)
    }

    loadPageRef.current = { toLoad: Int, token: CancelTokenSource ->
        val type = if (read) "read" else "unread"
        val typeFilter = if (filters.any()) "?type=${filters.joinToString(",") { it.name.lowercase() }}" else ""
        Axios.get<List<UserAlert>>(
            "${Config.apibase}/alerts/$type/$toLoad$typeFilter",
            generateConfig<String, List<UserAlert>>(token.token)
        ).then {
            return@then GenericSearchResponse.from(it.data)
        }
    }

    fun toURL(readLocal: Boolean? = read, filtersLocal: List<EAlertType>? = filters) {
        buildURL(
            listOfNotNull(
                includeIfNotNull(readLocal, "read"),
                if (filtersLocal?.any() == true) "type=${filtersLocal.joinToString(",") { it.name.lowercase() }}" else null
            ),
            "alerts", null, null, history
        )
    }

    fun markAll() =
        Axios.post<UserAlertStats>(
            "${Config.apibase}/alerts/markall",
            AlertUpdateAll(true),
            generateConfig<AlertUpdateAll, UserAlertStats>()
        ).then {
            updateAlertDisplay(it.data)

            setAlertStats(it.data)
            setForceHide(true)
        }.catch {
            // Bad request
        }

    fun setOptions(curationAlerts: Boolean, reviewAlerts: Boolean, followAlerts: Boolean) {
        setLoading(true)
        Axios.post<String>("${Config.apibase}/alerts/options", AlertOptionsRequest(curationAlerts, reviewAlerts, followAlerts), generateConfig<AlertOptionsRequest, String>()).then {
            setLoading(false)
            setAlertStats(
                alertStats?.copy(
                    curationAlerts = curationAlerts, reviewAlerts = reviewAlerts, followAlerts = followAlerts
                )
            )
        }.catch {
            setLoading(false)
        }
    }

    val markAlert = useCallback(hiddenAlerts) { hash: Int, stats: UserAlertStats ->
        setAlertStats(stats)
        setHiddenAlerts(hiddenAlerts + hash)
    }

    val renderer = useMemo(read, forceHide, hiddenAlerts) {
        InfiniteScrollElementRenderer<UserAlert> {
            alert {
                alert = it
                this.read = read
                hidden = (forceHide && it?.type != EAlertType.Collaboration) || hiddenAlerts.contains(it?.hashCode())
                this.markAlert = markAlert
            }
        }
    }

    div {
        className = ClassName("row")
        h1 {
            className = ClassName("col-lg-8")
            +"Alerts & Notifications"
        }
        div {
            className = ClassName("col-lg-4 d-flex")
            if (!read && alertStats?.let { it.unread > 0 } == true) {
                a {
                    href = "#"
                    className = ClassName("mark-read")
                    onClick = { e ->
                        e.preventDefault()
                        markAll()
                    }
                    +"Mark all as read"
                }
            }
        }
    }
    div {
        className = ClassName("row")
        div {
            className = ClassName("col-lg-4 alert-nav")
            div {
                className = ClassName("list-group")
                alertsListItem {
                    active = !read
                    count = alertStats?.unread
                    icon = "fa-envelope"
                    text = "Unread"
                    action = {
                        toURL(false)
                    }
                }
                alertsListItem {
                    active = read
                    count = alertStats?.read
                    icon = "fa-envelope-open"
                    text = "Read"
                    action = {
                        toURL(true)
                    }
                }
            }
            h6 {
                className = ClassName("mt-3")
                +"Filters"
            }
            div {
                className = ClassName("list-group")
                alertStats?.byType?.forEach { (type, count) ->
                    alertsListItem {
                        active = filters.contains(type)
                        this.count = count
                        icon = type.icon
                        text = type.readable()
                        action = {
                            toURL(
                                filtersLocal = if (filters.contains(type)) {
                                    filters.minus(type)
                                } else {
                                    filters.plus(type)
                                }
                            )
                        }
                    }
                }
            }
            h6 {
                className = ClassName("mt-3")
                +"Preferences"
            }
            if (alertStats != null) {
                div {
                    className = ClassName("list-group")
                    label {
                        className = ClassName("list-group-item list-group-item-action d-flex justify-content-between align-items-center")
                        htmlFor = "pref-map-curation"
                        role = AriaRole.button
                        span {
                            i {
                                className = ClassName("fas ${EAlertType.Curation.icon} me-2")
                            }
                            +"Map Curation"
                        }
                        span {
                            className = ClassName("form-switch")
                            input {
                                type = InputType.checkbox
                                className = ClassName("form-check-input")
                                id = "pref-map-curation"
                                disabled = loading
                                defaultChecked = alertStats.curationAlerts
                                onChange = { ev ->
                                    setOptions(
                                        ev.target.checked,
                                        alertStats.reviewAlerts,
                                        alertStats.followAlerts
                                    )
                                }
                            }
                        }
                    }
                    label {
                        className = ClassName("list-group-item list-group-item-action d-flex justify-content-between align-items-center")
                        htmlFor = "pref-map-review"
                        role = AriaRole.button
                        span {
                            i {
                                className = ClassName("fas ${EAlertType.Review.icon} me-2")
                            }
                            +"Map Review"
                        }
                        span {
                            className = ClassName("form-switch")
                            input {
                                type = InputType.checkbox
                                className = ClassName("form-check-input")
                                id = "pref-map-review"
                                disabled = loading
                                defaultChecked = alertStats.reviewAlerts
                                onChange = { ev ->
                                    setOptions(
                                        alertStats.curationAlerts,
                                        ev.target.checked,
                                        alertStats.followAlerts
                                    )
                                }
                            }
                        }
                    }
                    label {
                        className = ClassName("list-group-item list-group-item-action d-flex justify-content-between align-items-center")
                        htmlFor = "pref-follow"
                        role = AriaRole.button
                        span {
                            i {
                                className = ClassName("fas ${EAlertType.Follow.icon} me-2")
                            }
                            +"Follows"
                        }
                        span {
                            className = ClassName("form-switch")
                            input {
                                type = InputType.checkbox
                                className = ClassName("form-check-input")
                                id = "pref-follow"
                                disabled = loading
                                defaultChecked = alertStats.followAlerts
                                onChange = { ev ->
                                    setOptions(
                                        alertStats.curationAlerts,
                                        alertStats.reviewAlerts,
                                        ev.target.checked
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        div {
            className = ClassName("col-lg-8 vstack alerts")
            ref = resultsColumn
            key = "resultsColumn"

            alertInfiniteScroll {
                this.resetRef = resetRef
                rowHeight = 114.0
                itemsPerPage = 20
                container = resultsColumn
                loadPage = loadPageRef
                renderElement = renderer
            }
        }
    }
}

val alertInfiniteScroll = generateInfiniteScrollComponent(UserAlert::class)
