package io.beatmaps.user

import Axios
import external.TimeAgo
import generateConfig
import io.beatmaps.api.Alert
import io.beatmaps.common.DeletedData
import io.beatmaps.common.UnpublishData
import io.beatmaps.setPageTitle
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.dom.RDOMBuilder
import react.dom.br
import react.dom.div
import react.dom.h1
import react.dom.i
import react.dom.p
import react.dom.small
import react.dom.table
import react.dom.tbody
import react.dom.td
import react.dom.tr
import react.router.dom.routeLink
import react.setState

data class AlertsPageState(var alerts: List<Alert> = listOf(), var loading: Boolean = false) : RState

@JsExport
class AlertsPage : RComponent<RProps, AlertsPageState>() {
    init {
        state = AlertsPageState()
    }

    override fun componentDidMount() {
        setPageTitle("Alerts")

        loadState()
    }

    private fun loadState() {
        setState {
            loading = true
        }

        Axios.get<List<Alert>>(
            "/api/users/alerts",
            generateConfig<String, List<Alert>>()
        ).then {
            setState {
                alerts = it.data
                loading = false
            }
        }.catch {
            // Cancelled request
        }
    }

    fun RDOMBuilder<*>.simpleReason(title: String, reason: String) {
        i("fas fa-exclamation-circle mr-1") {}
        +title
        br { }
        +"Reason: $reason"
    }

    override fun RBuilder.render() {
        if (!state.loading && state.alerts.isEmpty()) {
            div("jumbotron") {
                h1 {
                    +"Nothing to see here"
                }
                p("mb-5 mt-5") {
                    +"You have no alerts"
                }
            }
        } else {
            table("table table-dark mapinfo-list") {
                tbody {
                    state.alerts.forEach {
                        tr {
                            td {
                                +it.map.name
                                p {
                                    routeLink("/profile/${it.map.uploader.id}") {
                                        +it.map.uploader.name
                                    }
                                    +" - "
                                    TimeAgo.default {
                                        attrs.date = it.map.uploaded.toString()
                                    }
                                    small {
                                        +it.map.description.replace("\n", " ")
                                    }
                                }
                            }
                            td {
                                when (it.action) {
                                    is UnpublishData -> simpleReason("Unpublished", it.action.reason)
                                    is DeletedData -> simpleReason("Deleted", it.action.reason)
                                    else -> simpleReason("Unknown", "")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
