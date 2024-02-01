package io.beatmaps.user.oauth

import external.axiosGet
import io.beatmaps.Config
import io.beatmaps.api.UserDetail
import io.beatmaps.common.json
import io.beatmaps.setPageTitle
import kotlinx.browser.window
import kotlinx.html.title
import react.Props
import react.dom.a
import react.dom.b
import react.dom.br
import react.dom.div
import react.dom.img
import react.fc
import react.useEffectOnce
import react.useState

external interface OauthHeaderProps : Props {
    var clientName: String
    var clientIcon: String?
    var callback: (Boolean) -> Unit
    var logoutLink: String?
}

val oauthHeader = fc<OauthHeaderProps> { props ->
    val (username, setUsername) = useState<String?>(null)
    val (avatar, setAvatar) = useState<String?>(null)

    useEffectOnce {
        setPageTitle("Login with BeatSaver")

        axiosGet<String>(
            "${Config.apibase}/users/me"
        ).then {
            // Decode is here so that 401 actually passes to error handler
            val data = json.decodeFromString<UserDetail>(it.data)

            setUsername(data.name)
            setAvatar(data.avatar)
            props.callback(true)
        }.catch {
            setUsername(null)
            setAvatar(null)
            props.callback(false)
        }
    }

    div("card-header") {
        props.clientIcon?.let {
            img("Icon", it, "oauthicon") {
                attrs.title = props.clientName
            }
        }
        b {
            +props.clientName
        }
        br {}
        +" wants to access your BeatSaver account"
        br {}
        username?.let {
            +"Hi, "
            b {
                +it
            }
            +" "
            img(src = avatar, classes = "authorize-avatar") {}
            br {}

            a(href = props.logoutLink ?: ("/oauth2/authorize/not-me" + window.location.search)) {
                +"Not you?"
            }
        }
    }
}
