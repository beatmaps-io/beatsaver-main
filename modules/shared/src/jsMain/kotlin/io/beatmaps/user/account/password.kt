package io.beatmaps.user.account

import external.Axios
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.AccountRequest
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.UserDetail
import io.beatmaps.shared.form.errors
import org.w3c.dom.HTMLInputElement
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.form
import react.dom.html.ReactHTML.h5
import react.dom.html.ReactHTML.hr
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.fc
import react.useRef
import react.useState
import web.autofill.AutoFillNormalField
import web.cssom.ClassName
import web.form.FormMethod
import web.html.ButtonType
import web.html.InputType

external interface ChangePasswordProps : Props {
    var userDetail: UserDetail
}

val changePassword = fc<ChangePasswordProps>("changePassword") { props ->
    val currpassRef = useRef<HTMLInputElement>()
    val passwordRef = useRef<HTMLInputElement>()
    val password2Ref = useRef<HTMLInputElement>()

    val (errors, setErrors) = useState(emptyList<String>())
    val (valid, setValid) = useState(false)
    val (loading, setLoading) = useState<Boolean>()

    form {
        attrs.className = ClassName("user-form")
        attrs.method = FormMethod.post
        attrs.onSubmit = { ev ->
            ev.preventDefault()

            Axios.post<ActionResponse>(
                "${Config.apibase}/users/me",
                AccountRequest(
                    currpassRef.current?.value ?: "",
                    passwordRef.current?.value ?: "",
                    password2Ref.current?.value ?: ""
                ),
                generateConfig<AccountRequest, ActionResponse>()
            ).then {
                if (it.data.success) {
                    currpassRef.current?.value = ""
                    passwordRef.current?.value = ""
                    password2Ref.current?.value = ""
                    setValid(true)
                    setErrors(listOf("Password updated"))
                    setLoading(false)
                } else {
                    setValid(false)
                    setErrors(it.data.errors)
                    setLoading(false)
                }
            }.catch {
                // Cancelled request
                setLoading(false)
            }
        }
        h5 {
            attrs.className = ClassName("mt-5")
            +"Password"
        }
        hr {
            attrs.className = ClassName("mt-2")
        }
        input {
            key = "hiddenname"
            attrs.type = InputType.text
            attrs.hidden = true
            attrs.name = "username"
            attrs.value = props.userDetail.name
            attrs.autoComplete = AutoFillNormalField.username
        }
        errors {
            attrs.errors = errors.take(1)
            attrs.valid = valid
        }
        div {
            attrs.className = ClassName("mb-3")
            label {
                attrs.className = ClassName("form-label")
                attrs.htmlFor = "current-password"
                +"Current Password"
            }
            input {
                key = "curpass"
                ref = currpassRef
                attrs.className = ClassName("form-control")
                attrs.type = InputType.password
                attrs.id = "current-password"
                attrs.required = true
                attrs.placeholder = "Current Password"
                attrs.autoComplete = AutoFillNormalField.currentPassword
            }
        }
        div {
            attrs.className = ClassName("mb-3")
            label {
                attrs.className = ClassName("form-label")
                attrs.htmlFor = "new-password"
                +"New Password"
            }
            input {
                key = "password"
                ref = passwordRef
                attrs.className = ClassName("form-control")
                attrs.type = InputType.password
                attrs.id = "new-password"
                attrs.required = true
                attrs.placeholder = "New Password"
                attrs.autoComplete = AutoFillNormalField.newPassword
            }
            input {
                key = "password2"
                ref = password2Ref
                attrs.className = ClassName("form-control")
                attrs.type = InputType.password
                attrs.id = "new-password2"
                attrs.required = true
                attrs.placeholder = "Repeat Password"
                attrs.autoComplete = AutoFillNormalField.newPassword
            }
            div {
                attrs.className = ClassName("d-grid")
                button {
                    attrs.className = ClassName("btn btn-success")
                    attrs.type = ButtonType.submit
                    attrs.disabled = loading == true
                    +"Change password"
                }
            }
        }
    }
}
