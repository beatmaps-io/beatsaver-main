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
import org.w3c.dom.HTMLInputElement
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
        attrs.className = ClassName("login-form card border-dark")
        div {
            attrs.className = ClassName("card-header")
            +"Pick a username"
        }
        form {
            attrs.className = ClassName("card-body")

            attrs.onSubmit = { ev ->
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
                attrs.className = ClassName("text-start")
                attrs.style = smallTextStyle
                +"Please pick a beatsaver username for your account. You will not be able to change this later."
            }
            p {
                attrs.className = ClassName("text-start")
                attrs.style = smallTextStyle
                +"Usernames must be made up of letters, numbers and any of "
                span {
                    attrs.className = ClassName("badge badge-secondary")
                    attrs.style = smallTextStyle
                    +". _ -"
                }
            }
            errors {
                attrs.errors = errors
            }
            input {
                attrs.type = InputType.text
                attrs.className = ClassName("form-control")
                ref = inputRef
                key = "username"
                attrs.name = "username"
                attrs.placeholder = "Username"
                attrs.disabled = loading
                attrs.required = true
                attrs.autoFocus = true
            }
            div {
                attrs.className = ClassName("d-grid")
                button {
                    attrs.className = ClassName("btn btn-success")
                    attrs.type = ButtonType.submit

                    attrs.disabled = submitted
                    i {
                        attrs.className = ClassName("fas fa-check")
                    }
                    +" Continue"
                }
            }
        }
    }
}
