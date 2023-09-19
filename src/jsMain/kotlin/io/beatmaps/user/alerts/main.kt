package io.beatmaps.user.alerts

import external.Axios
import external.CancelTokenSource
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.WithRouterProps
import io.beatmaps.api.AlertOptionsRequest
import io.beatmaps.api.AlertUpdateAll
import io.beatmaps.api.UserAlert
import io.beatmaps.api.UserAlertStats
import io.beatmaps.common.api.EAlertType
import io.beatmaps.common.json
import io.beatmaps.setPageTitle
import io.beatmaps.shared.InfiniteScroll
import io.beatmaps.shared.InfiniteScrollElementRenderer
import io.beatmaps.shared.buildURL
import io.beatmaps.shared.includeIfNotNull
import kotlinx.html.InputType
import kotlinx.html.js.onClickFunction
import kotlinx.serialization.decodeFromString
import org.w3c.dom.HTMLElement
import org.w3c.dom.url.URLSearchParams
import react.RBuilder
import react.RComponent
import react.State
import react.createRef
import react.dom.a
import react.dom.div
import react.dom.h1
import react.dom.h6
import react.dom.i
import react.dom.input
import react.dom.span
import react.setState

external interface AlertsPageProps : WithRouterProps

external interface AlertsPageState : State {
    var read: Boolean?
    var filters: List<EAlertType>?
    var alertStats: UserAlertStats?
    var resultsKey: Any?
    var hiddenAlerts: List<Int>?
    var forceHide: Boolean?
    var loading: Boolean?
}

class AlertsPage : RComponent<AlertsPageProps, AlertsPageState>() {
    private val resultsColumn = createRef<HTMLElement>()

    override fun componentDidMount() {
        setPageTitle("Alerts")
    }

    override fun componentWillMount() {
        Axios.get<String>(
            "${Config.apibase}/alerts/stats",
            generateConfig<String, String>()
        ).then {
            // Decode is here so that 401 actually passes to error handler
            val data = json.decodeFromString<UserAlertStats>(it.data)
            updateAlertDisplay(data)

            setState {
                alertStats = data
            }
        }.catch {
            if (it.asDynamic().response?.status == 401) {
                props.history.push("/login")
            }
        }
    }

    override fun componentWillReceiveProps(nextProps: AlertsPageProps) {
        setState {
            URLSearchParams(nextProps.location.search).let { u ->
                read = u.get("read")?.toBoolean()
                filters = u.get("type")?.split(",")?.mapNotNull { EAlertType.fromLower(it) }
            }

            resultsKey = Any()
            hiddenAlerts = listOf()
            forceHide = null
        }
    }

    private val loadPage = { toLoad: Int, token: CancelTokenSource ->
        val type = if (state.read == true) "read" else "unread"
        val typeFilter = if (state.filters?.any() == true) "?type=${state.filters?.joinToString(",") { it.name.lowercase() }}" else ""
        Axios.get<List<UserAlert>>(
            "${Config.apibase}/alerts/$type/$toLoad$typeFilter",
            generateConfig<String, List<UserAlert>>(token.token)
        ).then {
            return@then it.data
        }
    }

    private fun toURL(read: Boolean? = state.read, filters: List<EAlertType>? = state.filters) {
        buildURL(
            listOfNotNull(
                includeIfNotNull(read, "read"),
                if (filters?.any() == true) "type=${filters.joinToString(",") { it.name.lowercase() }}" else null
            ),
            "alerts", null, null, props.history
        )
    }

    private fun markAll() =
        Axios.post<UserAlertStats>(
            "${Config.apibase}/alerts/markall",
            AlertUpdateAll(true),
            generateConfig<AlertUpdateAll, UserAlertStats>()
        ).then {
            updateAlertDisplay(it.data)

            setState {
                alertStats = it.data
                forceHide = true
            }
        }.catch {
            // Bad request
        }

    private fun setOptions(curationAlerts: Boolean, reviewAlerts: Boolean) {
        setState { loading = true }
        Axios.post<String>("${Config.apibase}/alerts/options", AlertOptionsRequest(curationAlerts, reviewAlerts), generateConfig<AlertOptionsRequest, String>()).then {
            setState {
                loading = false
                alertStats = alertStats?.copy(
                    curationAlerts = curationAlerts, reviewAlerts = reviewAlerts
                )
            }
        }.catch {
            setState { loading = false }
        }
    }

    override fun RBuilder.render() {
        val stats = state.alertStats
        div("row") {
            h1("col-lg-8") {
                +"Alerts & Notifications"
            }
            div("col-lg-4 d-flex") {
                if (state.read != true && stats?.let { it.unread > 0 } == true) {
                    a("#", classes = "mark-read") {
                        attrs.onClickFunction = { e ->
                            e.preventDefault()
                            markAll()
                        }
                        +"Mark all as read"
                    }
                }
            }
        }
        div("row") {
            div("col-lg-4 alert-nav") {
                div("list-group") {
                    alertsListItem {
                        attrs.active = state.read != true
                        attrs.count = stats?.unread
                        attrs.icon = "fa-envelope"
                        attrs.text = "Unread"
                        attrs.action = {
                            toURL(false)
                        }
                    }
                    alertsListItem {
                        attrs.active = state.read == true
                        attrs.count = stats?.read
                        attrs.icon = "fa-envelope-open"
                        attrs.text = "Read"
                        attrs.action = {
                            toURL(true)
                        }
                    }
                }
                h6("mt-3") {
                    +"Filters"
                }
                div("list-group") {
                    stats?.byType?.forEach { (type, count) ->
                        alertsListItem {
                            attrs.active = state.filters?.contains(type) == true
                            attrs.count = count
                            attrs.icon = type.icon
                            attrs.text = type.readable()
                            attrs.action = {
                                toURL(
                                    filters = if (state.filters?.contains(type) == true) {
                                        state.filters?.minus(type)
                                    } else {
                                        (state.filters ?: emptyList()).plus(type)
                                    }
                                )
                            }
                        }
                    }
                }
                h6("mt-3") {
                    +"Preferences"
                }
                if (stats != null) {
                    div("list-group") {
                        a("#", classes = "list-group-item list-group-item-action d-flex justify-content-between align-items-center") {
                            attrs.onClickFunction = { ev ->
                                ev.preventDefault()
                                if (state.loading != true) setOptions(!stats.curationAlerts, stats.reviewAlerts)
                            }
                            span {
                                i("fas ${EAlertType.Curation.icon} me-2") {}
                                +"Map Curation"
                            }
                            span("form-switch") {
                                input(InputType.checkBox, classes = "form-check-input") {
                                    attrs.disabled = state.loading == true
                                    attrs.checked = stats.curationAlerts == true
                                }
                            }
                        }
                        a("#", classes = "list-group-item list-group-item-action d-flex justify-content-between align-items-center") {
                            attrs.onClickFunction = { ev ->
                                ev.preventDefault()
                                if (state.loading != true) setOptions(stats.curationAlerts, !stats.reviewAlerts)
                            }
                            span {
                                i("fas ${EAlertType.Review.icon} me-2") {}
                                +"Map Review"
                            }
                            span("form-switch") {
                                input(InputType.checkBox, classes = "form-check-input") {
                                    attrs.disabled = state.loading == true
                                    attrs.checked = stats.reviewAlerts == true
                                }
                            }
                        }
                    }
                }
            }
            div("col-lg-8 vstack alerts") {
                ref = resultsColumn
                key = "resultsColumn"

                child(AlertInfiniteScroll::class) {
                    attrs.resultsKey = state.resultsKey
                    attrs.rowHeight = 114.0
                    attrs.itemsPerPage = 20
                    attrs.container = resultsColumn
                    attrs.loadPage = loadPage
                    attrs.renderElement = InfiniteScrollElementRenderer {
                        alert {
                            alert = it
                            read = state.read
                            hidden = (state.forceHide == true && it?.type != EAlertType.Collaboration) ||
                                state.hiddenAlerts?.contains(it?.hashCode()) ?: false
                            markAlert = { stats ->
                                setState {
                                    alertStats = stats
                                    it?.hashCode()?.let { hc ->
                                        hiddenAlerts = (hiddenAlerts ?: emptyList()) + hc
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

class AlertInfiniteScroll : InfiniteScroll<UserAlert>()
