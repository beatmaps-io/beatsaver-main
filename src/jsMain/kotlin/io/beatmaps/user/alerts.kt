package io.beatmaps.user

import external.TimeAgo
import external.axiosGet
import io.beatmaps.api.UserAlert
import io.beatmaps.common.Config
import io.beatmaps.setPageTitle
import io.beatmaps.util.textToContent
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
import react.dom.b
import react.dom.div
import react.dom.h1
import react.dom.p
import react.dom.table
import react.dom.tbody
import react.dom.td
import react.dom.tr
import react.setState

external interface AlertsPageProps : RProps {
    var userId: Int?
    var visible: Boolean?
    var alertCountCallback: (Int) -> Unit
}

data class AlertsPageState(var alerts: List<UserAlert> = listOf(), var loading: Boolean = false) : RState

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

        axiosGet<List<UserAlert>>(
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
                                p {
                                    b {
                                        +it.head
                                    }
                                    +" - "
                                    TimeAgo.default {
                                        attrs.date = it.time.toString()
                                    }
                                }
                                p {
                                    textToContent(it.body)
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
