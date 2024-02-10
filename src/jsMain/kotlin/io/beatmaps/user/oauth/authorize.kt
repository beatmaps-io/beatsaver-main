package io.beatmaps.user.oauth

import io.beatmaps.common.json
import io.beatmaps.shared.form.errors
import io.beatmaps.user.loginForm
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.html.FormMethod
import kotlinx.serialization.Serializable
import org.w3c.dom.HTMLMetaElement
import org.w3c.dom.url.URLSearchParams
import react.Props
import react.dom.a
import react.dom.div
import react.dom.form
import react.dom.i
import react.dom.span
import react.fc
import react.useState

@Serializable
data class OauthData(val id: String, val name: String, val icon: String)

val authorizePage = fc<Props> {
    val (loggedIn, setLoggedIn) = useState<Boolean>()
    val params = URLSearchParams(window.location.search)
    val oauth = (document.querySelector("meta[name=\"oauth-data\"]") as? HTMLMetaElement)?.let {
        json.decodeFromString<OauthData>(it.content)
    }
    val clientName = oauth?.name ?: "An unknown application"

    div("login-form card border-dark") {
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
            div("card-body") {
                span { +"Loading..." }
            }
        } else if (loggedIn) {
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
