package io.beatmaps.user.oauth

import io.beatmaps.api.OauthScope
import react.Props
import react.dom.b
import react.dom.div
import react.dom.i
import react.dom.span
import react.fc
import react.useEffect
import react.useState

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

    div("scopes") {
        span("scopes-description") {
            +"This will allow "
            +props.clientName
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
}
