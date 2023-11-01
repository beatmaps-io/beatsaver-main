package io.beatmaps.user

import kotlinx.html.ButtonType
import kotlinx.html.InputType
import react.PropsWithChildren
import react.dom.a
import react.dom.button
import react.dom.div
import react.dom.i
import react.dom.input
import react.dom.p
import react.dom.span
import react.fc

external interface LoginFormProps : PropsWithChildren {
    var buttonText: String
    var discordLink: String?
}

val loginForm = fc<LoginFormProps> { props ->
    a(href = props.discordLink ?: "/discord", classes = "btn discord-btn") {
        span {
            i("fab fa-discord") {}
            +" Sign in with discord"
        }
    }
    p {
        +"OR"
    }
    props.children()
    input(type = InputType.text, classes = "form-control") {
        key = "username"
        attrs.name = "username"
        attrs.placeholder = "Username"
        attrs.required = true
        attrs.autoFocus = true
        attrs.attributes["autocomplete"] = "username"
    }
    input(type = InputType.password, classes = "form-control") {
        key = "password"
        attrs.name = "password"
        attrs.placeholder = "Password"
        attrs.required = true
        attrs.attributes["autocomplete"] = "current-password"
    }
    div("d-grid") {
        button(classes = "btn btn-success", type = ButtonType.submit) {
            i("fas fa-sign-in-alt") {}
            +" ${props.buttonText}"
        }
    }
}
