package io.beatmaps.user.account

import external.Axios
import external.generateConfig
import external.reactFor
import io.beatmaps.Config
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.EmailRequest
import io.beatmaps.api.UserDetail
import io.beatmaps.captcha.ICaptchaHandler
import io.beatmaps.shared.form.errors
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.id
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLInputElement
import react.Props
import react.RefObject
import react.dom.button
import react.dom.div
import react.dom.input
import react.dom.label
import react.fc
import react.useRef
import react.useState

external interface AccountEmailProps : Props {
    var userDetail: UserDetail
    var captchaRef: RefObject<ICaptchaHandler>
}

val accountEmail = fc<AccountEmailProps>("accountEmail") { props ->
    val (email, setEmail) = useState(props.userDetail.email ?: "")
    val (errors, setErrors) = useState(emptyList<String>())
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

                        props.captchaRef.current?.execute()?.then { captcha ->
                            props.captchaRef.current?.reset()

                            Axios.post<ActionResponse>(
                                "${Config.apibase}/users/email",
                                EmailRequest(captcha, email),
                                generateConfig<EmailRequest, ActionResponse>()
                            ).then {
                                setValid(it.data.success)
                                setErrors(
                                    if (it.data.success) listOf("Email sent") else it.data.errors
                                )
                            }
                        }?.catch {
                            // Cancelled request
                            setValid(false)
                            setErrors(listOfNotNull(it.message))
                        }?.finally {
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
