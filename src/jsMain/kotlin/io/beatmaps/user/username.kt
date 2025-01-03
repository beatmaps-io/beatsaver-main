package io.beatmaps.user

import external.Axios
import external.axiosGet
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.History
import io.beatmaps.api.AccountDetailReq
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.UserDetail
import io.beatmaps.common.json
import io.beatmaps.setPageTitle
import io.beatmaps.shared.form.errors
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.js.onSubmitFunction
import kotlinx.serialization.decodeFromString
import org.w3c.dom.HTMLInputElement
import react.Props
import react.dom.button
import react.dom.div
import react.dom.form
import react.dom.i
import react.dom.input
import react.dom.jsStyle
import react.dom.p
import react.dom.span
import react.fc
import react.router.useNavigate
import react.useEffectOnce
import react.useRef
import react.useState

val pickUsernamePage = fc<Props> {
    val (submitted, setSubmitted) = useState(false)
    val (loading, setLoading) = useState(false)
    val (errors, setErrors) = useState(emptyList<String>())

    val inputRef = useRef<HTMLInputElement>()
    val history = History(useNavigate())

    useEffectOnce {
        setPageTitle("Set username")
        setLoading(true)

        axiosGet<String>(
            "${Config.apibase}/users/me"
        ).then {
            // Decode is here so that 401 actually passes to error handler
            val data = json.decodeFromString<UserDetail>(it.data)

            if (data.uniqueSet) {
                history.push("/")
            } else {
                val re = Regex("[^._\\-A-Za-z0-9]")
                inputRef.current?.value = re.replace(data.name.replace(' ', '-'), "")
                setLoading(false)
            }
        }.catch {
            if (it.asDynamic().response?.status == 401) {
                history.push("/login")
            }
            // Cancelled request
        }
    }

    div("login-form card border-dark") {
        div("card-header") {
            +"Pick a username"
        }
        form(classes = "card-body") {
            attrs.onSubmitFunction = { ev ->
                ev.preventDefault()
                setSubmitted(true)

                Axios.post<ActionResponse>(
                    "${Config.apibase}/users/username",
                    AccountDetailReq(inputRef.current?.value ?: ""),
                    generateConfig<AccountDetailReq, ActionResponse>()
                ).then {
                    if (it.data.success) {
                        history.push("/")
                    } else {
                        setErrors(it.data.errors)
                        setSubmitted(false)
                    }
                }.catch {
                    // Cancelled request
                    setSubmitted(false)
                }
            }
            p("text-start") {
                attrs.jsStyle {
                    fontSize = "0.8rem"
                }
                +"Please pick a beatsaver username for your account. You will not be able to change this later."
            }
            p("text-start") {
                attrs.jsStyle {
                    fontSize = "0.8rem"
                }
                +"Usernames must be made up of letters, numbers and any of "
                span("badge badge-secondary") {
                    attrs.jsStyle {
                        fontSize = "0.8rem"
                    }
                    +". _ -"
                }
            }
            errors {
                attrs.errors = errors
            }
            input(type = InputType.text, classes = "form-control") {
                ref = inputRef
                key = "username"
                attrs.name = "username"
                attrs.placeholder = "Username"
                attrs.disabled = loading
                attrs.required = true
                attrs.autoFocus = true
            }
            div("d-grid") {
                button(classes = "btn btn-success", type = ButtonType.submit) {
                    attrs.disabled = submitted
                    i("fas fa-check") {}
                    +" Continue"
                }
            }
        }
    }
}
