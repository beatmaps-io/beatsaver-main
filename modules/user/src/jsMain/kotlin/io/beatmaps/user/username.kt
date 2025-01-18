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
import io.beatmaps.util.fcmemo
import js.objects.jso
import react.CSSProperties
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.form
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import react.router.useNavigate
import react.useEffectOnce
import react.useRef
import react.useState
import web.cssom.ClassName
import web.cssom.rem
import web.html.ButtonType
import web.html.HTMLInputElement
import web.html.InputType

val pickUsernamePage = fcmemo<Props>("pickUsernamePage") {
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

    div {
        className = ClassName("login-form card border-dark")
        div {
            className = ClassName("card-header")
            +"Pick a username"
        }
        form {
            className = ClassName("card-body")

            onSubmit = { ev ->
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
            val smallTextStyle = jso<CSSProperties> {
                fontSize = 0.8.rem
            }
            p {
                className = ClassName("text-start")
                style = smallTextStyle
                +"Please pick a beatsaver username for your account. You will not be able to change this later."
            }
            p {
                className = ClassName("text-start")
                style = smallTextStyle
                +"Usernames must be made up of letters, numbers and any of "
                span {
                    className = ClassName("badge badge-secondary")
                    style = smallTextStyle
                    +". _ -"
                }
            }
            errors {
                this.errors = errors
            }
            input {
                type = InputType.text
                className = ClassName("form-control")
                ref = inputRef
                key = "username"
                name = "username"
                placeholder = "Username"
                disabled = loading
                required = true
                autoFocus = true
            }
            div {
                className = ClassName("d-grid")
                button {
                    className = ClassName("btn btn-success")
                    type = ButtonType.submit

                    disabled = submitted
                    i {
                        className = ClassName("fas fa-check")
                    }
                    +" Continue"
                }
            }
        }
    }
}
