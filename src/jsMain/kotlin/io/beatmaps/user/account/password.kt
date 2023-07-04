package io.beatmaps.user.account

import external.Axios
import external.generateConfig
import external.reactFor
import io.beatmaps.Config
import io.beatmaps.api.AccountRequest
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.UserDetail
import kotlinx.html.ButtonType
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.hidden
import kotlinx.html.id
import kotlinx.html.js.onSubmitFunction
import org.w3c.dom.HTMLInputElement
import react.Props
import react.dom.button
import react.dom.div
import react.dom.form
import react.dom.h5
import react.dom.hr
import react.dom.input
import react.dom.jsStyle
import react.dom.label
import react.fc
import react.useRef
import react.useState

external interface ChangePasswordProps : Props {
    var userDetail: UserDetail
}

val changePassword = fc<ChangePasswordProps> { props ->
    val currpassRef = useRef<HTMLInputElement>()
    val passwordRef = useRef<HTMLInputElement>()
    val password2Ref = useRef<HTMLInputElement>()

    val (errors, setErrors) = useState<List<String>>()
    val (loading, setLoading) = useState<Boolean>()

    form(classes = "user-form", action = "/profile", method = FormMethod.post) {
        attrs.onSubmitFunction = { ev ->
            ev.preventDefault()

            Axios.post<ActionResponse>(
                "${Config.apibase}/users/me",
                AccountRequest(
                    currpassRef.current?.value ?: "",
                    passwordRef.current?.value ?: "",
                    password2Ref.current?.value ?: "",
                ),
                generateConfig<AccountRequest, ActionResponse>()
            ).then {
                if (it.data.success) {
                    currpassRef.current?.value = ""
                    passwordRef.current?.value = ""
                    password2Ref.current?.value = ""
                    setErrors(listOf("Password updated"))
                    setLoading(false)
                } else {
                    setErrors(it.data.errors)
                    setLoading(false)
                }
            }.catch {
                // Cancelled request
                setLoading(false)
            }
        }
        h5("mt-5") {
            +"Password"
        }
        hr("mt-2") {}
        input(InputType.text) {
            attrs.hidden = true
            key = "hiddenname"
            attrs.name = "username"
            attrs.value = props.userDetail.name
            attrs.attributes["autocomplete"] = "username"
        }
        div("invalid-feedback") {
            val error = errors?.firstOrNull()
            if (error != null) {
                attrs.jsStyle {
                    display = "block"
                }
                +error
            }
        }
        div("mb-3") {
            label("form-label") {
                attrs.reactFor = "current-password"
                +"Current Password"
            }
            input(InputType.password, classes = "form-control") {
                key = "curpass"
                ref = currpassRef
                attrs.id = "current-password"
                attrs.required = true
                attrs.placeholder = "Current Password"
                attrs.attributes["autocomplete"] = "current-password"
            }
        }
        div("mb-3") {
            label("form-label") {
                attrs.reactFor = "new-password"
                +"New Password"
            }
            input(InputType.password, classes = "form-control") {
                key = "password"
                ref = passwordRef
                attrs.id = "new-password"
                attrs.required = true
                attrs.placeholder = "New Password"
                attrs.attributes["autocomplete"] = "new-password"
            }
            input(InputType.password, classes = "form-control") {
                key = "password2"
                ref = password2Ref
                attrs.id = "new-password2"
                attrs.required = true
                attrs.placeholder = "Repeat Password"
                attrs.attributes["autocomplete"] = "new-password"
            }
            div("d-grid") {
                button(classes = "btn btn-success", type = ButtonType.submit) {
                    attrs.disabled = loading == true
                    +"Change password"
                }
            }
        }
    }
}
