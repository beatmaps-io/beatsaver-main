package io.beatmaps.user.account

import external.Axios
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.AccountRequest
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.UserDetail
import io.beatmaps.shared.form.errors
import io.beatmaps.util.fcmemo
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.form
import react.dom.html.ReactHTML.h5
import react.dom.html.ReactHTML.hr
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.useRef
import react.useState
import web.autofill.AutoFillNormalField
import web.cssom.ClassName
import web.form.FormMethod
import web.html.ButtonType
import web.html.HTMLInputElement
import web.html.InputType

external interface ChangePasswordProps : Props {
    var userDetail: UserDetail
}

val changePassword = fcmemo<ChangePasswordProps>("changePassword") { props ->
    val currpassRef = useRef<HTMLInputElement>()
    val passwordRef = useRef<HTMLInputElement>()
    val password2Ref = useRef<HTMLInputElement>()

    val (errors, setErrors) = useState(emptyList<String>())
    val (valid, setValid) = useState(false)
    val (loading, setLoading) = useState<Boolean>()

    form {
        className = ClassName("user-form")
        method = FormMethod.post
        onSubmit = { ev ->
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
            className = ClassName("mt-5")
            +"Password"
        }
        hr {
            className = ClassName("mt-2")
        }
        input {
            key = "hiddenname"
            type = InputType.text
            hidden = true
            name = "username"
            value = props.userDetail.name
            autoComplete = AutoFillNormalField.username
        }
        errors {
            this.errors = errors.take(1)
            this.valid = valid
        }
        div {
            className = ClassName("mb-3")
            label {
                className = ClassName("form-label")
                htmlFor = "current-password"
                +"Current Password"
            }
            input {
                key = "curpass"
                ref = currpassRef
                className = ClassName("form-control")
                type = InputType.password
                id = "current-password"
                required = true
                placeholder = "Current Password"
                autoComplete = AutoFillNormalField.currentPassword
            }
        }
        div {
            className = ClassName("mb-3")
            label {
                className = ClassName("form-label")
                htmlFor = "new-password"
                +"New Password"
            }
            input {
                key = "password"
                ref = passwordRef
                className = ClassName("form-control")
                type = InputType.password
                id = "new-password"
                required = true
                placeholder = "New Password"
                autoComplete = AutoFillNormalField.newPassword
            }
            input {
                key = "password2"
                ref = password2Ref
                className = ClassName("form-control")
                type = InputType.password
                id = "new-password2"
                required = true
                placeholder = "Repeat Password"
                autoComplete = AutoFillNormalField.newPassword
            }
            div {
                className = ClassName("d-grid")
                button {
                    className = ClassName("btn btn-success")
                    type = ButtonType.submit
                    disabled = loading == true
                    +"Change password"
                }
            }
        }
    }
}
