package io.beatmaps.user.alerts

import external.Axios
import external.CancelTokenSource
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.WithRouterProps
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
import react.setState

external interface AlertsPageProps : WithRouterProps

external interface AlertsPageState : State {
    var read: Boolean?
    var filters: List<EAlertType>?
    var alertStats: UserAlertStats?
    var resultsKey: Any?
    var hiddenAlerts: List<Int>?
    var forceHide: Boolean?
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

    override fun RBuilder.render() {
        div("row") {
            h1("col-lg-8") {
                +"Alerts & Notifications"
            }
            div("col-lg-4 d-flex") {
                if (state.read != true && state.alertStats?.let { it.unread > 0 } == true) {
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
                        attrs.count = state.alertStats?.unread
                        attrs.icon = "fa-envelope"
                        attrs.text = "Unread"
                        attrs.action = {
                            toURL(false)
                        }
                    }
                    alertsListItem {
                        attrs.active = state.read == true
                        attrs.count = state.alertStats?.read
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
                    state.alertStats?.byType?.forEach { (type, count) ->
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
                            hidden = state.forceHide ?: state.hiddenAlerts?.contains(it?.id)
                            markAlert = { stats ->
                                setState {
                                    alertStats = stats
                                    it?.id?.let { id ->
                                        hiddenAlerts = (hiddenAlerts ?: emptyList()) + id
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
