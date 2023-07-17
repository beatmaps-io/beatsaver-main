package io.beatmaps.user.account

import external.Axios
import external.generateConfig
import external.reactFor
import io.beatmaps.Config
import io.beatmaps.api.AccountDetailReq
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.UserDetail
import io.beatmaps.shared.errors
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.id
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLInputElement
import react.Props
import react.dom.button
import react.dom.div
import react.dom.input
import react.dom.label
import react.fc
import react.useRef
import react.useState

external interface AccountEmailProps : Props {
    var userDetail: UserDetail
}

val accountEmail = fc<AccountEmailProps> { props ->
    val (email, setEmail) = useState(props.userDetail.email ?: "")
    val (errors, setErrors) = useState(listOf<String>())
    val (valid, setValid) = useState(false)
    val (loading, setLoading) = useState(false)
    val emailRef = useRef<HTMLInputElement>()

    errors {
        attrs.errors = errors.take(1)
        attrs.valid = valid
    }
    div("mb-3") {
        label("col-sm-2 col-form-label") {
            attrs.reactFor = "email"
            +"Email"
        }
        input(InputType.text, classes = "form-control") {
            key = "email"
            attrs.id = "email"
            attrs.value = email
            attrs.onChangeFunction = {
                setEmail(emailRef.current?.value ?: "")
            }
            ref = emailRef
        }
        div("d-grid") {
            button(classes = "btn btn-success", type = ButtonType.submit) {
                attrs.onClickFunction = { ev ->
                    ev.preventDefault()

                    if (props.userDetail.email == email) {
                        setErrors(listOf("That's already your email!"))
                    } else {
                        setLoading(true)

                        Axios.post<ActionResponse>(
                            "${Config.apibase}/users/email",
                            AccountDetailReq(email),
                            generateConfig<AccountDetailReq, ActionResponse>()
                        ).then {
                            if (it.data.success) {
                                setValid(true)
                                setErrors(listOf("Email sent"))
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
                }
                attrs.disabled = loading
                +"Change email"
            }
        }
    }
}
