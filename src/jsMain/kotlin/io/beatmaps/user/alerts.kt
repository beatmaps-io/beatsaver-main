package io.beatmaps.user

import external.Axios
import external.TimeAgo
import external.axiosGet
import external.generateConfig
import io.beatmaps.api.AlertUpdate
import io.beatmaps.api.UserAlert
import io.beatmaps.common.Config
import io.beatmaps.index.coloredCard
import io.beatmaps.setPageTitle
import io.beatmaps.util.textToContent
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
import react.dom.a
import react.dom.b
import react.dom.div
import react.dom.h1
import react.dom.i
import react.dom.p
import react.dom.span
import react.setState

external interface AlertsPageProps : RProps {
    var userId: Int?
    var visible: Boolean?
    var alertCountCallback: (Int) -> Unit
}

data class AlertsPageState(var unreadAlerts: List<UserAlert> = listOf(), var loading: Boolean = false) : RState

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
            "${Config.apibase}/alerts/unread" + if (props.userId == null) "" else "/${props.userId}",
        ).then {
            setState {
                unreadAlerts = it.data
                loading = false
            }
            props.alertCountCallback(it.data.size)
        }.catch {
            // Cancelled request
        }
    }

    private fun markAlert(alert: UserAlert, read: Boolean) {
        Axios.post<String>("${Config.apibase}/alerts/mark", AlertUpdate(alert.id, read), generateConfig<AlertUpdate, String>())
            .then {
                setState {
                    unreadAlerts = if (read) unreadAlerts - alert else unreadAlerts + alert
                }
                props.alertCountCallback(state.unreadAlerts.size)
            }
    }

    override fun RBuilder.render() {
        if (props.visible != true) return

        if (!state.loading && state.unreadAlerts.isEmpty()) {
            div("jumbotron") {
                h1 {
                    +"Nothing to see here"
                }
                p("mb-5 mt-5") {
                    +"You have no alerts"
                }
            }
        } else {
            state.unreadAlerts.forEach { alert ->
                coloredCard {
                    attrs.color = alert.type.color
                    attrs.icon = alert.type.icon

                    div("card-header d-flex") {
                        span {
                            b {
                                +alert.head
                            }
                            +" - "
                            TimeAgo.default {
                                attrs.date = alert.time.toString()
                            }
                        }
                        if (props.userId == null) {
                            div("ms-auto flex-shrink-0") {
                                a("#") {
                                    attrs.title = "Mark as read"
                                    attrs.onClickFunction = {
                                        it.preventDefault()
                                        markAlert(alert, true)
                                    }

                                    i("fas fa-eye-slash text-info") { }
                                }
                            }
                        }
                    }
                    div("card-body") { textToContent(alert.body) }
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
