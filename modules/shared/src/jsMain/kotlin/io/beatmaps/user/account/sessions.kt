package io.beatmaps.user.account

import io.beatmaps.api.SessionInfo
import io.beatmaps.api.SessionsData
import io.beatmaps.shared.ModalButton
import io.beatmaps.shared.ModalData
import io.beatmaps.shared.modalContext
import io.beatmaps.util.fcmemo
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h5
import react.dom.html.ReactHTML.hr
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.use
import react.useState
import web.cssom.ClassName
import web.html.ButtonType
import kotlin.js.Promise

external interface ManageSessionsProps : Props {
    var sessions: SessionsData
    var removeSessionCallback: (SessionInfo) -> Unit
    var revokeAllCallback: () -> Promise<Boolean>
}

val manageSessions = fcmemo<ManageSessionsProps>("manageSessions") { props ->
    val (full, setFull) = useState<Boolean>()

    val modal = use(modalContext)

    div {
        className = ClassName("user-form")
        h5 {
            className = ClassName("mt-5")
            +"Authorised sessions"
        }
        hr {}
        if (full == true) {
            table {
                className = ClassName("table table-dark table-striped mappers text-nowrap")
                tbody {
                    props.sessions.oauth.forEach {
                        oauthRow {
                            key = it.id
                            session = it
                            removeSessionCallback = props.removeSessionCallback
                        }
                    }

                    props.sessions.site.forEach {
                        siteRow {
                            key = it.id
                            session = it
                            removeSessionCallback = props.removeSessionCallback
                        }
                    }
                }
            }
        }
        div {
            className = ClassName("mb-3 row")
            div {
                className = ClassName("col pt-1")
                +"Oauth apps: ${props.sessions.oauth.size}, Site logins: ${props.sessions.site.size}"
            }
            div {
                className = ClassName("col col-sm-5 text-end")
                button {
                    className = ClassName("d-inline-block btn btn-info m-0")
                    type = ButtonType.submit
                    onClick = {
                        it.preventDefault()
                        setFull(full != true)
                    }
                    +(if (full == true) "Show less" else "Show more")
                }
                if (props.sessions.oauth.isNotEmpty() || props.sessions.site.any { c -> !c.current }) {
                    button {
                        className = ClassName("d-inline-block btn btn-danger ms-2 m-0")
                        type = ButtonType.submit
                        onClick = {
                            it.preventDefault()
                            modal?.current?.showDialog?.invoke(
                                ModalData(
                                    "Are you sure?",
                                    "This will completely log out of BeatSaver on every device you're currently logged in on.",
                                    listOf(ModalButton("Log Out", "primary", props.revokeAllCallback), ModalButton("Cancel"))
                                )
                            )
                        }
                        +"Revoke all"
                    }
                }
            }
        }
    }
}
