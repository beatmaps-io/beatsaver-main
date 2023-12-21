package io.beatmaps.user

import external.axiosGet
import io.beatmaps.Config
import io.beatmaps.api.OauthScope
import io.beatmaps.api.UserDetail
import io.beatmaps.common.json
import io.beatmaps.setPageTitle
import io.beatmaps.shared.form.errors
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.html.FormMethod
import kotlinx.html.title
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import org.w3c.dom.HTMLMetaElement
import org.w3c.dom.url.URLSearchParams
import react.Props
import react.dom.a
import react.dom.b
import react.dom.br
import react.dom.div
import react.dom.form
import react.dom.i
import react.dom.img
import react.dom.span
import react.fc
import react.useEffectOnce
import react.useState

@Serializable
data class OauthData(val id: String, val name: String, val icon: String)

val authorizePage = fc<Props> {
    val (loading, setLoading) = useState(false)
    val (username, setUsername) = useState<String?>(null)
    val (avatar, setAvatar) = useState<String?>(null)

    useEffectOnce {
        setPageTitle("Login with BeatSaver")

        setLoading(true)

        axiosGet<String>(
            "${Config.apibase}/users/me"
        ).then {
            // Decode is here so that 401 actually passes to error handler
            val data = json.decodeFromString<UserDetail>(it.data)

            setLoading(false)
            setUsername(data.name)
            setAvatar(data.avatar)
        }.catch {
            setLoading(false)
            setUsername(null)
            setAvatar(null)
        }
    }

    val params = URLSearchParams(window.location.search)
    val oauth = (document.querySelector("meta[name=\"oauth-data\"]") as? HTMLMetaElement)?.let {
        json.decodeFromString<OauthData>(it.content)
    }
    val clientName = oauth?.name ?: "An unknown application"
    val scopes = (params.get("scope") ?: "").split(" ").map {
        OauthScope.fromTag(it)
    }

    div("login-form card border-dark") {
        div("card-header") {
            oauth?.icon?.let {
                if (it != "null") {
                    img("Icon", it, "oauthicon") {
                        attrs.title = clientName
                    }
                }
            }
            b {
                +clientName
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

                a(href = "authorize/not-me" + window.location.search) {
                    +"Not you?"
                }
            }
        }
        div("scopes") {
            span("scopes-description") {
                +"This will allow "
                +clientName
                +" to:"
            }

            for (scope in scopes) {
                div("scope") {
                    scope?.description?.let {
                        i("fas fa-user-check") {}
                        span { b { +"  $it" } }
                    } ?: run {
                        span { b { +"  Replace all your maps with RickRoll" } }
                    }
                }
            }
        }
        if (loading) {
            div("card-body") {
                span { +"Loading..." }
            }
        } else if (username != null) {
            div("card-body d-grid") {
                a("/oauth2/authorize/success" + window.location.search, classes = "btn btn-success") {
                    i("fas fa-sign-in-alt") {}
                    +" Authorize"
                }
            }
        } else {
            val search = window.location.search
            form(classes = "card-body", method = FormMethod.post, action = "/oauth2/authorize$search") {
                val serialized = search.encodeToByteArray().joinToString("") {
                    (0xFF and it.toInt()).toString(16).padStart(2, '0')
                }

                loginForm {
                    if (params.has("failed")) {
                        errors {
                            attrs.errors = listOf("Username or password not valid")
                        }
                    }

                    attrs.discordLink = "/discord?state=$serialized"
                    attrs.buttonText = "Authorize"
                }
            }
        }
    }
}
