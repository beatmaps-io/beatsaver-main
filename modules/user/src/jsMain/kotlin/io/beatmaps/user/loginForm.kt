package io.beatmaps.user

import react.PropsWithChildren
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import react.fc
import web.autofill.AutoFillNormalField
import web.cssom.ClassName
import web.html.ButtonType
import web.html.InputType

external interface LoginFormProps : PropsWithChildren {
    var buttonText: String
    var discordLink: String?
}

val loginForm = fc<LoginFormProps>("loginForm") { props ->
    a {
        attrs.href = props.discordLink ?: "/discord"
        attrs.className = ClassName("btn discord-btn")
        span {
            i {
                attrs.className = ClassName("fab fa-discord")
            }
            +" Sign in with discord"
        }
    }
    p {
        +"OR"
    }
    props.children()
    input {
        attrs.type = InputType.text
        attrs.className = ClassName("form-control")
        key = "username"
        attrs.name = "username"
        attrs.placeholder = "Username"
        attrs.required = true
        attrs.autoFocus = true
        attrs.autoComplete = AutoFillNormalField.username
    }
    input {
        attrs.type = InputType.password
        attrs.className = ClassName("form-control")
        key = "password"
        attrs.name = "password"
        attrs.placeholder = "Password"
        attrs.required = true
        attrs.autoComplete = AutoFillNormalField.currentPassword
    }
    div {
        attrs.className = ClassName("d-grid")
        button {
            attrs.className = ClassName("btn btn-success")
            attrs.type = ButtonType.submit

            i {
                attrs.className = ClassName("fas fa-sign-in-alt")
            }
            +" ${props.buttonText}"
        }
    }
}
