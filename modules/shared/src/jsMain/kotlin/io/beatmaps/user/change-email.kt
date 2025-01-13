package io.beatmaps.user

import external.Axios
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.History
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.ChangeEmailRequest
import io.beatmaps.setPageTitle
import io.beatmaps.shared.form.errors
import io.beatmaps.util.parseJwt
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.id
import kotlinx.html.js.onSubmitFunction
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.dom.HTMLInputElement
import react.Props
import react.dom.button
import react.dom.div
import react.dom.form
import react.dom.input
import react.dom.label
import react.fc
import react.router.useNavigate
import react.router.useParams
import react.useEffectOnce
import react.useRef
import react.useState

val changeEmailPage = fc<Props>("changeEmailPage") {
    val (loading, setLoading) = useState(false)
    val (errors, setErrors) = useState(emptyList<String>())
    val passwordRef = useRef<HTMLInputElement>()
    val params = useParams()
    val history = History(useNavigate())

    val (parsedJwt, _) = useState(parseJwt(params["jwt"] ?: ""))

    useEffectOnce {
        setPageTitle("Change Email")
    }

    div("login-form card border-dark") {
        div("card-header") {
            +"Change email"
        }
        form(classes = "card-body pt-2") {
            attrs.onSubmitFunction = { ev ->
                ev.preventDefault()

                setLoading(true)

                Axios.post<ActionResponse>(
                    "${Config.apibase}/users/change-email",
                    ChangeEmailRequest(
                        params["jwt"] ?: "",
                        passwordRef.current?.value ?: ""
                    ),
                    generateConfig<ChangeEmailRequest, ActionResponse>()
                ).then {
                    if (it.data.success) {
                        history.push("/login?email")
                    } else {
                        setErrors(it.data.errors)
                        setLoading(false)
                    }
                }.catch {
                    // Cancelled request
                    setLoading(false)
                }
            }
            errors {
                attrs.errors = errors
            }
            label("form-label float-start mt-2") {
                attrs.htmlFor = "email"
                +"New email"
            }
            input(type = InputType.email, classes = "form-control") {
                key = "email"
                attrs.id = "email"
                attrs.disabled = true
                attrs.value = parsedJwt["email"]?.jsonPrimitive?.content ?: ""
            }
            if (parsedJwt["action"]?.jsonPrimitive?.content != "reclaim") {
                label("form-label float-start mt-3") {
                    attrs.htmlFor = "password"
                    +"Verify password"
                }
                input(type = InputType.password, classes = "form-control") {
                    key = "password"
                    ref = passwordRef
                    attrs.id = "password"
                    attrs.placeholder = "************"
                    attrs.required = true
                    attrs.autoFocus = true
                    attrs.attributes["autocomplete"] = "new-password"
                }
            }
            div("d-grid") {
                button(classes = "btn btn-success", type = ButtonType.submit) {
                    attrs.disabled = loading
                    +"Change email"
                }
            }
        }
    }
}
