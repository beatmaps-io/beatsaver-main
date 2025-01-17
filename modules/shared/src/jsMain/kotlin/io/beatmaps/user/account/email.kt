package io.beatmaps.user.account

import external.Axios
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.EmailRequest
import io.beatmaps.api.UserDetail
import io.beatmaps.captcha.ICaptchaHandler
import io.beatmaps.shared.form.errors
import org.w3c.dom.HTMLInputElement
import react.Props
import react.RefObject
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.fc
import react.useRef
import react.useState
import web.cssom.ClassName
import web.html.ButtonType
import web.html.InputType

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
    div {
        attrs.className = ClassName("mb-3")
        label {
            attrs.className = ClassName("col-sm-2 col-form-label")
            attrs.htmlFor = "email"
            +"Email"
        }
        input {
            key = "email"
            attrs.type = InputType.text
            attrs.className = ClassName("form-control")
            attrs.id = "email"
            attrs.value = email
            attrs.onChange = {
                setEmail(emailRef.current?.value ?: "")
            }
            ref = emailRef
        }
        div {
            attrs.className = ClassName("d-grid")
            button {
                attrs.className = ClassName("btn btn-success")
                attrs.type = ButtonType.submit
                attrs.onClick = { ev ->
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
                                generateConfig<EmailRequest, ActionResponse>(validStatus = arrayOf(200, 400))
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
