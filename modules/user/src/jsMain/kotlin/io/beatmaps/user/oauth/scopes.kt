package io.beatmaps.user.oauth

import io.beatmaps.api.OauthScope
import react.Props
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.span
import react.fc
import react.useEffect
import react.useState
import web.cssom.ClassName

external interface OauthScopeProps : Props {
    var scopes: String
    var clientName: String
}

val oauthScopes = fc<OauthScopeProps>("oauthScopes") { props ->
    val (scopes, setScopes) = useState(listOf<OauthScope?>())
    useEffect(props.scopes) {
        setScopes(
            props.scopes.split(" ").map {
                OauthScope.fromTag(it)
            }
        )
    }

    div {
        attrs.className = ClassName("scopes")
        span {
            attrs.className = ClassName("scopes-description")
            +"This will allow "
            +props.clientName
            +" to:"
        }

        for (scope in scopes) {
            div {
                attrs.className = ClassName("scope")
                scope?.description?.let {
                    i {
                        attrs.className = ClassName("fas fa-user-check")
                    }
                    span { b { +"  $it" } }
                } ?: run {
                    span { b { +"  Replace all your maps with RickRoll" } }
                }
            }
        }
    }
}
