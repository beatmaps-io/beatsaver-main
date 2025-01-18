package io.beatmaps.user

import io.beatmaps.util.fcmemo
import react.PropsWithChildren
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import web.autofill.AutoFillNormalField
import web.cssom.ClassName
import web.html.ButtonType
import web.html.InputType

external interface LoginFormProps : PropsWithChildren {
    var buttonText: String
    var discordLink: String?
}

val loginForm = fcmemo<LoginFormProps>("loginForm") { props ->
    a {
        href = props.discordLink ?: "/discord"
        className = ClassName("btn discord-btn")
        span {
            i {
                className = ClassName("fab fa-discord")
            }
            +" Sign in with discord"
        }
    }
    p {
        +"OR"
    }
    +props.children
    input {
        type = InputType.text
        className = ClassName("form-control")
        key = "username"
        name = "username"
        placeholder = "Username"
        required = true
        autoFocus = true
        autoComplete = AutoFillNormalField.username
    }
    input {
        type = InputType.password
        className = ClassName("form-control")
        key = "password"
        name = "password"
        placeholder = "Password"
        required = true
        autoComplete = AutoFillNormalField.currentPassword
    }
    div {
        className = ClassName("d-grid")
        button {
            className = ClassName("btn btn-success")
            type = ButtonType.submit

            i {
                className = ClassName("fas fa-sign-in-alt")
            }
            +" ${props.buttonText}"
        }
    }
}
