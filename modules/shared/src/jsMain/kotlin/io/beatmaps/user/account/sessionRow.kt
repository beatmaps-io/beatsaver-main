package io.beatmaps.user.account

import external.TimeAgo
import external.axiosDelete
import io.beatmaps.Config
import io.beatmaps.api.OauthSession
import io.beatmaps.api.SessionInfo
import io.beatmaps.api.SessionRevokeRequest
import io.beatmaps.api.SiteSession
import io.beatmaps.util.fcmemo
import js.objects.jso
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.tr
import react.useState
import web.cssom.ClassName
import web.cssom.Margin
import web.cssom.px
import web.html.ButtonType
import kotlin.time.Duration.Companion.days

external interface SiteRowProps : Props {
    var session: SiteSession
    var removeSessionCallback: (SessionInfo) -> Unit
}

val siteRow = fcmemo<SiteRowProps>("siteRow") { props ->
    tr {
        td {
            props.session.countryCode?.let { cc ->
                img {
                    alt = cc
                    src = "https://flagcdn.com/24x18/${cc.lowercase()}.png"
                    width = 24.0
                    style = jso { margin = Margin(0.px, 8.px) }
                    title = cc
                }
            } ?: i {
                className = ClassName("fas fa-question-circle")
                style = jso { width = 40.px }
                title = "Unknown"
            }
        }
        td {
            className = ClassName("w-100")
            +(if (props.session.current) "This session" else "Site login")
        }
        sessionRowCommon {
            session = props.session
            days = 7
            removeSessionCallback = props.removeSessionCallback
        }
    }
}

external interface OauthRowProps : Props {
    var session: OauthSession
    var removeSessionCallback: (SessionInfo) -> Unit
}

val oauthRow = fcmemo<OauthRowProps>("oauthRow") { props ->
    tr {
        td {
            style = jso {
                width = 40.px
            }
            props.session.clientIcon?.let { ci ->
                img {
                    alt = "Icon"
                    src = ci
                    title = props.session.clientName
                    style = jso { margin = Margin(0.px, 8.px) }
                    width = 24.0
                }
            } ?: i {
                className = ClassName("fas fa-question-circle")
                style = jso { width = 40.px }
                title = props.session.clientName
            }
        }
        td {
            className = ClassName("w-100")
            +props.session.clientName
            i {
                className = ClassName("fas fa-info-circle ms-1")
                title = props.session.scopes.joinToString("\n") { scope -> scope.description }
            }
        }
        sessionRowCommon {
            session = props.session
            days = 45
            removeSessionCallback = props.removeSessionCallback
        }
    }
}

external interface SessionRowProps : Props {
    var session: SessionInfo
    var days: Int
    var removeSessionCallback: (SessionInfo) -> Unit
}

val sessionRowCommon = fcmemo<SessionRowProps>("sessionRowCommon") { props ->
    val session = props.session
    val (loading, setLoading) = useState<Boolean>()

    td {
        className = ClassName("text-end col-sm-2")
        TimeAgo.default {
            minPeriod = 60
            date = session.expiry.minus(props.days.days).toString()
        }
    }
    td {
        if (!(session is SiteSession && session.current)) {
            button {
                className = ClassName("btn btn-danger m-0")
                type = ButtonType.submit
                disabled = loading == true
                onClick = {
                    it.preventDefault()
                    setLoading(true)
                    axiosDelete<SessionRevokeRequest, String>("${Config.apibase}/users/sessions/${session.id}", SessionRevokeRequest(site = session is SiteSession)).then {
                        props.removeSessionCallback(session)
                    }.catch {
                        setLoading(false)
                    }
                }
                i {
                    className = ClassName("fas fa-trash")
                }
            }
        }
    }
}
