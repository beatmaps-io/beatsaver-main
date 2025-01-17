package io.beatmaps.user.oauth

import io.beatmaps.common.json
import io.beatmaps.shared.form.errors
import io.beatmaps.user.loginForm
import io.beatmaps.util.fcmemo
import io.beatmaps.util.form
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.serialization.Serializable
import org.w3c.dom.HTMLMetaElement
import org.w3c.dom.url.URLSearchParams
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.span
import react.useState
import web.cssom.ClassName
import web.form.FormMethod

@Serializable
data class OauthData(val id: String, val name: String, val icon: String)

val authorizePage = fcmemo<Props>("authorizePage") {
    val (loggedIn, setLoggedIn) = useState<Boolean>()
    val params = URLSearchParams(window.location.search)
    val oauth = (document.querySelector("meta[name=\"oauth-data\"]") as? HTMLMetaElement)?.let {
        json.decodeFromString<OauthData>(it.content)
    }
    val clientName = oauth?.name ?: "An unknown application"

    div {
        attrs.className = ClassName("login-form card border-dark")
        oauthHeader {
            attrs.clientName = clientName
            attrs.clientIcon = oauth?.icon?.let { if (it == "null") null else it }
            attrs.callback = {
                setLoggedIn(it)
            }
        }
        oauthScopes {
            attrs.clientName = clientName
            attrs.scopes = params.get("scope") ?: ""
        }
        if (loggedIn == null) {
            div {
                attrs.className = ClassName("card-body")
                span { +"Loading..." }
            }
        } else if (loggedIn) {
            div {
                attrs.className = ClassName("card-body d-grid")
                a {
                    attrs.href = "/oauth2/authorize/success" + window.location.search
                    attrs.className = ClassName("btn btn-success")
                    i {
                        attrs.className = ClassName("fas fa-sign-in-alt")
                    }
                    +" Authorize"
                }
            }
        } else {
            val search = window.location.search
            form("card-body", FormMethod.post, "/oauth2/authorize$search") {
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
