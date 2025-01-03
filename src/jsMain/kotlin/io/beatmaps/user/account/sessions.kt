package io.beatmaps.user.account

import io.beatmaps.api.SessionInfo
import io.beatmaps.api.SessionsData
import io.beatmaps.index.ModalButton
import io.beatmaps.index.ModalData
import io.beatmaps.index.modalContext
import kotlinx.html.ButtonType
import kotlinx.html.js.onClickFunction
import react.Props
import react.dom.button
import react.dom.div
import react.dom.h5
import react.dom.hr
import react.dom.table
import react.dom.tbody
import react.fc
import react.useContext
import react.useState
import kotlin.js.Promise

external interface ManageSessionsProps : Props {
    var sessions: SessionsData
    var removeSessionCallback: (SessionInfo) -> Unit
    var revokeAllCallback: () -> Promise<Boolean>
}

val manageSessions = fc<ManageSessionsProps>("manageSessions") { props ->
    val (full, setFull) = useState<Boolean>()

    val modal = useContext(modalContext)

    div(classes = "user-form") {
        h5("mt-5") {
            +"Authorised sessions"
        }
        hr {}
        if (full == true) {
            table("table table-dark table-striped mappers text-nowrap") {
                tbody {
                    props.sessions.oauth.forEach {
                        oauthRow {
                            key = it.id
                            attrs.session = it
                            attrs.removeSessionCallback = props.removeSessionCallback
                        }
                    }

                    props.sessions.site.forEach {
                        siteRow {
                            key = it.id
                            attrs.session = it
                            attrs.removeSessionCallback = props.removeSessionCallback
                        }
                    }
                }
            }
        }
        div("mb-3 row") {
            div("col pt-1") {
                +"Oauth apps: ${props.sessions.oauth.size}, Site logins: ${props.sessions.site.size}"
            }
            div("col col-sm-5 text-end") {
                button(classes = "d-inline-block btn btn-info m-0", type = ButtonType.submit) {
                    attrs.onClickFunction = {
                        it.preventDefault()
                        setFull(full != true)
                    }
                    +(if (full == true) "Show less" else "Show more")
                }
                if (props.sessions.oauth.isNotEmpty() || props.sessions.site.any { c -> !c.current }) {
                    button(classes = "d-inline-block btn btn-danger ms-2 m-0", type = ButtonType.submit) {
                        attrs.onClickFunction = {
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
