package io.beatmaps.user.account

import external.TimeAgo
import external.axiosDelete
import io.beatmaps.Config
import io.beatmaps.api.OauthSession
import io.beatmaps.api.SessionInfo
import io.beatmaps.api.SessionRevokeRequest
import io.beatmaps.api.SiteSession
import js.objects.jso
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.tr
import react.fc
import react.useState
import web.cssom.ClassName
import web.cssom.Margin
import web.cssom.px
import web.html.ButtonType
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
                img {
                    attrs.alt = cc
                    attrs.src = "https://flagcdn.com/24x18/${cc.lowercase()}.png"
                    attrs.width = 24.0
                    attrs.style = jso { margin = Margin(0.px, 8.px) }
                    attrs.title = cc
                }
            } ?: i {
                attrs.className = ClassName("fas fa-question-circle")
                attrs.style = jso { width = 40.px }
                attrs.title = "Unknown"
            }
        }
        td {
            attrs.className = ClassName("w-100")
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
            attrs.style = jso {
                width = 40.px
            }
            props.session.clientIcon?.let { ci ->
                img {
                    attrs.alt = "Icon"
                    attrs.src = ci
                    attrs.title = props.session.clientName
                    attrs.style = jso { margin = Margin(0.px, 8.px) }
                    attrs.width = 24.0
                }
            } ?: i {
                attrs.className = ClassName("fas fa-question-circle")
                attrs.style = jso { width = 40.px }
                attrs.title = props.session.clientName
            }
        }
        td {
            attrs.className = ClassName("w-100")
            +props.session.clientName
            i {
                attrs.className = ClassName("fas fa-info-circle ms-1")
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

    td {
        attrs.className = ClassName("text-end col-sm-2")
        TimeAgo.default {
            attrs.minPeriod = 60
            attrs.date = session.expiry.minus(props.days.toDuration(DurationUnit.DAYS)).toString()
        }
    }
    td {
        if (!(session is SiteSession && session.current)) {
            button {
                attrs.className = ClassName("btn btn-danger m-0")
                attrs.type = ButtonType.submit
                attrs.disabled = loading == true
                attrs.onClick = {
                    it.preventDefault()
                    setLoading(true)
                    axiosDelete<SessionRevokeRequest, String>("${Config.apibase}/users/sessions/${session.id}", SessionRevokeRequest(site = session is SiteSession)).then {
                        props.removeSessionCallback(session)
                    }.catch {
                        setLoading(false)
                    }
                }
                i {
                    attrs.className = ClassName("fas fa-trash")
                }
            }
        }
    }
}
