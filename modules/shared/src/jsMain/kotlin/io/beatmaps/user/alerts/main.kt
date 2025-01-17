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
import org.w3c.dom.HTMLElement
import org.w3c.dom.url.URLSearchParams
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
import web.html.InputType
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
                attrs.alert = it
                attrs.read = read
                attrs.hidden = (forceHide && it?.type != EAlertType.Collaboration) || hiddenAlerts.contains(it?.hashCode())
                attrs.markAlert = markAlert
            }
        }
    }

    div {
        attrs.className = ClassName("row")
        h1 {
            attrs.className = ClassName("col-lg-8")
            +"Alerts & Notifications"
        }
        div {
            attrs.className = ClassName("col-lg-4 d-flex")
            if (!read && alertStats?.let { it.unread > 0 } == true) {
                a {
                    attrs.href = "#"
                    attrs.className = ClassName("mark-read")
                    attrs.onClick = { e ->
                        e.preventDefault()
                        markAll()
                    }
                    +"Mark all as read"
                }
            }
        }
    }
    div {
        attrs.className = ClassName("row")
        div {
            attrs.className = ClassName("col-lg-4 alert-nav")
            div {
                attrs.className = ClassName("list-group")
                alertsListItem {
                    attrs.active = !read
                    attrs.count = alertStats?.unread
                    attrs.icon = "fa-envelope"
                    attrs.text = "Unread"
                    attrs.action = {
                        toURL(false)
                    }
                }
                alertsListItem {
                    attrs.active = read
                    attrs.count = alertStats?.read
                    attrs.icon = "fa-envelope-open"
                    attrs.text = "Read"
                    attrs.action = {
                        toURL(true)
                    }
                }
            }
            h6 {
                attrs.className = ClassName("mt-3")
                +"Filters"
            }
            div {
                attrs.className = ClassName("list-group")
                alertStats?.byType?.forEach { (type, count) ->
                    alertsListItem {
                        attrs.active = filters.contains(type)
                        attrs.count = count
                        attrs.icon = type.icon
                        attrs.text = type.readable()
                        attrs.action = {
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
                attrs.className = ClassName("mt-3")
                +"Preferences"
            }
            if (alertStats != null) {
                div {
                    attrs.className = ClassName("list-group")
                    label {
                        attrs.className = ClassName("list-group-item list-group-item-action d-flex justify-content-between align-items-center")
                        attrs.htmlFor = "pref-map-curation"
                        attrs.role = AriaRole.button
                        span {
                            i {
                                attrs.className = ClassName("fas ${EAlertType.Curation.icon} me-2")
                            }
                            +"Map Curation"
                        }
                        span {
                            attrs.className = ClassName("form-switch")
                            input {
                                attrs.type = InputType.checkbox
                                attrs.className = ClassName("form-check-input")
                                attrs.id = "pref-map-curation"
                                attrs.disabled = loading
                                attrs.defaultChecked = alertStats.curationAlerts
                                attrs.onChange = { ev ->
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
                        attrs.className = ClassName("list-group-item list-group-item-action d-flex justify-content-between align-items-center")
                        attrs.htmlFor = "pref-map-review"
                        attrs.role = AriaRole.button
                        span {
                            i {
                                attrs.className = ClassName("fas ${EAlertType.Review.icon} me-2")
                            }
                            +"Map Review"
                        }
                        span {
                            attrs.className = ClassName("form-switch")
                            input {
                                attrs.type = InputType.checkbox
                                attrs.className = ClassName("form-check-input")
                                attrs.id = "pref-map-review"
                                attrs.disabled = loading
                                attrs.defaultChecked = alertStats.reviewAlerts
                                attrs.onChange = { ev ->
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
                        attrs.className = ClassName("list-group-item list-group-item-action d-flex justify-content-between align-items-center")
                        attrs.htmlFor = "pref-follow"
                        attrs.role = AriaRole.button
                        span {
                            i {
                                attrs.className = ClassName("fas ${EAlertType.Follow.icon} me-2")
                            }
                            +"Follows"
                        }
                        span {
                            attrs.className = ClassName("form-switch")
                            input {
                                attrs.type = InputType.checkbox
                                attrs.className = ClassName("form-check-input")
                                attrs.id = "pref-follow"
                                attrs.disabled = loading
                                attrs.defaultChecked = alertStats.followAlerts
                                attrs.onChange = { ev ->
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
            attrs.className = ClassName("col-lg-8 vstack alerts")
            ref = resultsColumn
            key = "resultsColumn"

            alertInfiniteScroll {
                attrs.resetRef = resetRef
                attrs.rowHeight = 114.0
                attrs.itemsPerPage = 20
                attrs.container = resultsColumn
                attrs.loadPage = loadPageRef
                attrs.renderElement = renderer
            }
        }
    }
}

val alertInfiniteScroll = generateInfiniteScrollComponent(UserAlert::class)
