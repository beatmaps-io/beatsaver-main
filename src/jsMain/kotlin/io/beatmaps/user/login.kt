package io.beatmaps.user

import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.id
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
import react.dom.a
import react.dom.button
import react.dom.form
import react.dom.h1
import react.dom.hr
import react.dom.i
import react.dom.input
import react.dom.p
import react.dom.span

@JsExport
class LoginPage : RComponent<RProps, RState>() {
    override fun RBuilder.render() {
        form(classes = "login-form") {
            h1("h3 mb-3 font-weight-normal") {
                +"Sign in"
            }
            button(type = ButtonType.button, classes = "btn discord-btn") {
                span {
                    i("fab fa-discord") {}
                    +"Sign in with discord"
                }
            }
            p {
                +"OR"
            }
            input(type = InputType.email) {
                attrs.id = "inputEmail"
                attrs.placeholder = "Username"
                attrs.required = true
                attrs.autoFocus = true
            }
            input(type = InputType.password) {
                attrs.id = "inputPassword"
                attrs.placeholder = "Password"
                attrs.required = true
            }
            button(classes = "btn btn-success btn-block", type = ButtonType.submit) {
                i("fas ga-sign-in-alt") {}
                +"Sign in"
            }
            a("#") {
                attrs.id = "forgot_pwd"
                +"Forgot password?"
            }
            hr {}
            button(classes = "btn btn-primary btn-block", type = ButtonType.button) {
                attrs.id = "btn-signup"
                i("fas fa-user-plus") {}
                +"Sign up new account"
            }
        }
    }
}

/*
<div id="logreg-forms">
            <form action="/reset/password/" class="form-reset">
                <input type="email" id="resetEmail" class="form-control" placeholder="Email address" required="" autofocus="">
                <button class="btn btn-primary btn-block" type="submit">Reset Password</button>
                <a href="#" id="cancel_reset"><i class="fas fa-angle-left"></i> Back</a>
            </form>

            <form action="/signup/" class="form-signup">
                <div class="social-login">
                    <button class="btn facebook-btn social-btn" type="button"><span><i class="fab fa-facebook-f"></i> Sign up with Facebook</span> </button>
                </div>
                <div class="social-login">
                    <button class="btn google-btn social-btn" type="button"><span><i class="fab fa-google-plus-g"></i> Sign up with Google+</span> </button>
                </div>

                <p style="text-align:center">OR</p>

                <input type="text" id="user-name" class="form-control" placeholder="Full name" required="" autofocus="">
                <input type="email" id="user-email" class="form-control" placeholder="Email address" required autofocus="">
                <input type="password" id="user-pass" class="form-control" placeholder="Password" required autofocus="">
                <input type="password" id="user-repeatpass" class="form-control" placeholder="Repeat Password" required autofocus="">

                <button class="btn btn-primary btn-block" type="submit"><i class="fas fa-user-plus"></i> Sign Up</button>
                <a href="#" id="cancel_signup"><i class="fas fa-angle-left"></i> Back</a>
            </form>
            <br>

    </div>
 */

fun RBuilder.loginPage(handler: RProps.() -> Unit): ReactElement {
    return child(LoginPage::class) {
        this.attrs(handler)
    }
}
