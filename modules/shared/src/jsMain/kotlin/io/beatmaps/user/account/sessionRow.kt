package io.beatmaps.user.account

import external.TimeAgo
import external.axiosDelete
import io.beatmaps.Config
import io.beatmaps.api.OauthSession
import io.beatmaps.api.SessionInfo
import io.beatmaps.api.SessionRevokeRequest
import io.beatmaps.api.SiteSession
import kotlinx.html.ButtonType
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import react.Props
import react.dom.button
import react.dom.i
import react.dom.img
import react.dom.jsStyle
import react.dom.td
import react.dom.tr
import react.fc
import react.useState
import kotlin.time.DurationUnit
import kotlin.time.toDuration

external interface SiteRowProps : Props {
    var session: SiteSession
    var removeSessionCallback: (SessionInfo) -> Unit
}

val siteRow = fc<SiteRowProps>("siteRow") { props ->
    tr {
        td {
            props.session.countryCode?.let { cc ->
                img(cc, "https://flagcdn.com/24x18/${cc.lowercase()}.png") {
                    attrs.width = "24"
                    attrs.jsStyle { margin = "0 8px" }
                    attrs.title = cc
                }
            } ?: i("fas fa-question-circle") {
                attrs.jsStyle { width = "40px" }
                attrs.title = "Unknown"
            }
        }
        td("w-100") {
            +(if (props.session.current) "This session" else "Site login")
        }
        sessionRowCommon {
            attrs.session = props.session
            attrs.days = 7
            attrs.removeSessionCallback = props.removeSessionCallback
        }
    }
}

external interface OauthRowProps : Props {
    var session: OauthSession
    var removeSessionCallback: (SessionInfo) -> Unit
}

val oauthRow = fc<OauthRowProps>("oauthRow") { props ->
    tr {
        td {
            attrs.jsStyle {
                width = "40px"
            }
            props.session.clientIcon?.let { ci ->
                img("Icon", ci) {
                    attrs.title = props.session.clientName
                    attrs.jsStyle { margin = "0 8px" }
                    attrs.width = "24"
                }
            } ?: i("fas fa-question-circle") {
                attrs.jsStyle { width = "40px" }
                attrs.title = props.session.clientName
            }
        }
        td("w-100") {
            +props.session.clientName
            i("fas fa-info-circle ms-1") {
                attrs.title = props.session.scopes.joinToString("\n") { scope -> scope.description }
            }
        }
        sessionRowCommon {
            attrs.session = props.session
            attrs.days = 45
            attrs.removeSessionCallback = props.removeSessionCallback
        }
    }
}

external interface SessionRowProps : Props {
    var session: SessionInfo
    var days: Int
    var removeSessionCallback: (SessionInfo) -> Unit
}

val sessionRowCommon = fc<SessionRowProps>("sessionRowCommon") { props ->
    val session = props.session
    val (loading, setLoading) = useState<Boolean>()

    td("text-end col-sm-2") {
        TimeAgo.default {
            attrs.minPeriod = 60
            attrs.date = session.expiry.minus(props.days.toDuration(DurationUnit.DAYS)).toString()
        }
    }
    td {
        if (!(session is SiteSession && session.current)) {
            button(classes = "btn btn-danger m-0", type = ButtonType.submit) {
                attrs.disabled = loading == true
                attrs.onClickFunction = {
                    it.preventDefault()
                    setLoading(true)
                    axiosDelete<SessionRevokeRequest, String>("${Config.apibase}/users/sessions/${session.id}", SessionRevokeRequest(site = session is SiteSession)).then {
                        props.removeSessionCallback(session)
                    }.catch {
                        setLoading(false)
                    }
                }
                i("fas fa-trash") {}
            }
        }
    }
}
