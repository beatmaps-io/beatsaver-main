package io.beatmaps.user

import external.TimeAgo
import external.axiosGet
import io.beatmaps.api.Alert
import io.beatmaps.common.Config
import io.beatmaps.common.DeletedData
import io.beatmaps.common.UnpublishData
import io.beatmaps.setPageTitle
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
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

external interface AlertsPageProps : RProps {
    var userId: Int?
    var visible: Boolean?
    var alertCountCallback: (Int) -> Unit
}

data class AlertsPageState(var alerts: List<Alert> = listOf(), var loading: Boolean = false) : RState

class AlertsPage : RComponent<AlertsPageProps, AlertsPageState>() {
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

        axiosGet<List<Alert>>(
            "${Config.apibase}/users/alerts" + if (props.userId == null) "" else "/${props.userId}",
        ).then {
            setState {
                alerts = it.data
                loading = false
            }
            props.alertCountCallback(it.data.size)
        }.catch {
            // Cancelled request
        }
    }

    fun RDOMBuilder<*>.simpleReason(title: String, reason: String) {
        i("fas fa-exclamation-circle me-1") {}
        +title
        br { }
        +"Reason: $reason"
    }

    override fun RBuilder.render() {
        if (props.visible != true) return

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
                                routeLink("/maps/${it.map.id}") {
                                    if (it.map.name.isNotBlank()) {
                                        +it.map.name
                                    } else {
                                        +"<NO NAME>"
                                    }
                                }
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

fun RBuilder.alertsPage(handler: AlertsPageProps.() -> Unit): ReactElement {
    return child(AlertsPage::class) {
        this.attrs(handler)
    }
}
