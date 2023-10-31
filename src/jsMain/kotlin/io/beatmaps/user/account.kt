package io.beatmaps.user

import external.Axios
import external.ReCAPTCHA
import external.axiosDelete
import external.axiosGet
import external.generateConfig
import external.reactFor
import external.recaptcha
import io.beatmaps.Config
import io.beatmaps.api.AccountDetailReq
import io.beatmaps.api.AccountType
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.SessionInfo
import io.beatmaps.api.SessionRevokeRequest
import io.beatmaps.api.SessionsData
import io.beatmaps.api.UserDetail
import io.beatmaps.shared.errors
import io.beatmaps.upload.UploadRequestConfig
import io.beatmaps.user.account.accountEmail
import io.beatmaps.user.account.changePassword
import io.beatmaps.user.account.manageSessions
import kotlinx.html.ButtonType
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.hidden
import kotlinx.html.id
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import kotlinx.html.role
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.files.get
import org.w3c.xhr.FormData
import react.Props
import react.RBuilder
import react.RComponent
import react.State
import react.createRef
import react.dom.a
import react.dom.button
import react.dom.div
import react.dom.form
import react.dom.h5
import react.dom.hr
import react.dom.input
import react.dom.label
import react.dom.span
import react.dom.textarea
import react.dom.value
import react.setState

external interface AccountComponentProps : Props {
    var userDetail: UserDetail
    var onUpdate: () -> Unit
}

external interface AccountComponentState : State {
    var loading: Boolean?
    var userLoading: Boolean?
    var uploading: Boolean?
    var errors: List<String>
    var usernameErrors: List<String>
    var username: String
    var descriptionErrors: List<String>
    var description: String
    var sessions: SessionsData?
}

class AccountComponent : RComponent<AccountComponentProps, AccountComponentState>() {
    private val avatarRef = createRef<HTMLInputElement>()
    private val progressBarInnerRef = createRef<HTMLElement>()

    private val usernameRef = createRef<HTMLInputElement>()
    private val descriptionRef = createRef<HTMLTextAreaElement>()

    private val captchaRef = createRef<ReCAPTCHA>()

    override fun componentWillMount() {
        setState {
            loading = false
            uploading = false
            userLoading = false
            errors = listOf()
            usernameErrors = listOf()
            username = props.userDetail.name
            descriptionErrors = listOf()
            description = props.userDetail.description ?: ""
        }
    }

    override fun componentDidMount() {
        axiosGet<SessionsData>(
            "${Config.apibase}/users/sessions"
        ).then {
            setState {
                sessions = it.data
            }
        }.catch {
            // Ignore
        }
    }

    private fun removeSessionCallback(session: SessionInfo) {
        setState {
            sessions = SessionsData(
                sessions?.oauth?.filter { c -> c.id != session.id } ?: listOf(),
                sessions?.site?.filter { c -> c.id != session.id } ?: listOf()
            )
        }
    }

    private fun revokeAll() {
        axiosDelete<SessionRevokeRequest, String>("${Config.apibase}/users/sessions", SessionRevokeRequest(site = true)).then {
            setState {
                sessions = SessionsData(listOf(), sessions?.site?.filter { c -> c.current } ?: listOf())
            }
        }
    }

    override fun RBuilder.render() {
        // Having 2 forms confuses password managers, only the password section needs to invoke them
        div(classes = "user-form") {
            h5("mt-5") {
                +"Account details"
            }
            hr("mt-2") {}
            if (props.userDetail.type != AccountType.DISCORD) {
                accountEmail {
                    attrs.userDetail = props.userDetail
                    attrs.captchaRef = captchaRef
                }
            }
            errors {
                attrs.errors = state.usernameErrors.take(1)
            }
            div("mb-3") {
                label("form-label") {
                    attrs.reactFor = "name"
                    +"Username"
                }
                input(InputType.text, classes = "form-control") {
                    key = "username"
                    attrs.id = "name"
                    attrs.value = state.username
                    attrs.onChangeFunction = {
                        setState {
                            username = usernameRef.current?.value ?: ""
                        }
                    }
                    ref = usernameRef
                }
                div("d-grid") {
                    button(classes = "btn btn-success", type = ButtonType.submit) {
                        attrs.onClickFunction = { ev ->
                            ev.preventDefault()

                            if (props.userDetail.name == state.username) {
                                setState {
                                    usernameErrors = listOf("That's already your username!")
                                }
                            } else {
                                setState {
                                    userLoading = true
                                }

                                Axios.post<ActionResponse>(
                                    "${Config.apibase}/users/username",
                                    AccountDetailReq(state.username),
                                    generateConfig<AccountDetailReq, ActionResponse>()
                                ).then {
                                    if (it.data.success) {
                                        props.onUpdate()
                                        setState {
                                            userLoading = false
                                        }
                                    } else {
                                        setState {
                                            usernameErrors = it.data.errors
                                            userLoading = false
                                        }
                                    }
                                }.catch {
                                    // Cancelled request
                                    setState {
                                        userLoading = false
                                    }
                                }
                            }
                        }
                        attrs.disabled = state.userLoading == true
                        +"Change username"
                    }
                }
            }
            errors {
                attrs.errors = state.descriptionErrors.take(1)
            }
            div("mb-3") {
                label("form-label") {
                    attrs.reactFor = "description"
                    +"Description"
                }
                textarea(classes = "form-control") {
                    key = "description"
                    attrs.id = "description"
                    attrs.value = state.description
                    attrs.rows = "5"
                    attrs.maxLength = "500"
                    attrs.onChangeFunction = {
                        setState {
                            description = descriptionRef.current?.value ?: ""
                        }
                    }
                    ref = descriptionRef
                }
                span("badge badge-" + if (state.description.length > 480) "danger" else "dark") {
                    attrs.id = "count_message"
                    +"${state.description.length} / 500"
                }
                div("d-grid") {
                    button(classes = "btn btn-success", type = ButtonType.submit) {
                        attrs.onClickFunction = { ev ->
                            ev.preventDefault()

                            if (props.userDetail.description != state.description) {
                                setState {
                                    userLoading = true
                                }

                                Axios.post<ActionResponse>(
                                    "${Config.apibase}/users/description",
                                    AccountDetailReq(state.description),
                                    generateConfig<AccountDetailReq, ActionResponse>()
                                ).then {
                                    if (it.data.success) {
                                        props.onUpdate()
                                        setState {
                                            userLoading = false
                                        }
                                    } else {
                                        setState {
                                            descriptionErrors = it.data.errors
                                            userLoading = false
                                        }
                                    }
                                }.catch {
                                    // Cancelled request
                                    setState {
                                        userLoading = false
                                    }
                                }
                            }
                        }
                        attrs.disabled = state.userLoading == true
                        +"Change description"
                    }
                }
            }
        }
        if (props.userDetail.type == AccountType.SIMPLE) {
            div(classes = "user-form") {
                h5("mt-5") {
                    +"Avatar"
                }
                hr("mt-2") {}
                div("mb-3") {
                    input(InputType.file) {
                        key = "avatar"
                        ref = avatarRef
                        attrs.hidden = state.uploading == true
                    }
                    div("d-grid") {
                        button(classes = "btn btn-success", type = ButtonType.submit) {
                            attrs.hidden = state.uploading == true
                            attrs.onClickFunction = { ev ->
                                ev.preventDefault()

                                val file = avatarRef.current?.files?.let { it[0] }
                                if (file != null) {
                                    setState {
                                        uploading = true
                                    }

                                    val data = FormData()
                                    data.asDynamic().append("file", file)

                                    Axios.post<dynamic>(
                                        "/avatar", data,
                                        UploadRequestConfig { progress ->
                                            val v = ((progress.loaded * 100f) / progress.total).toInt()
                                            progressBarInnerRef.current?.style?.width = "$v%"
                                        }
                                    ).then { r ->
                                        if (r.status == 200) {
                                            props.onUpdate()
                                            setState {
                                                uploading = false
                                            }
                                        } else {
                                            setState {
                                                uploading = false
                                                avatarRef.current?.value = ""
                                            }
                                        }
                                    }.catch {
                                        setState {
                                            uploading = false
                                            avatarRef.current?.value = ""
                                        }
                                    }
                                }
                            }
                            +"Upload"
                        }
                    }
                    div("progress") {
                        attrs.hidden = state.uploading == false
                        div("progress-bar progress-bar-striped progress-bar-animated bg-info") {
                            attrs.role = "progressbar"
                            ref = progressBarInnerRef
                        }
                    }
                }
            }
        }
        if (props.userDetail.type != AccountType.DISCORD) {
            changePassword {
                attrs.userDetail = props.userDetail
            }
        }
        state.sessions?.let { s ->
            manageSessions {
                attrs.revokeAllCallback = ::revokeAll
                attrs.removeSessionCallback = ::removeSessionCallback
                attrs.sessions = s
            }
        }
        if (props.userDetail.type != AccountType.DISCORD) {
            form(classes = "user-form", action = "/profile/unlink-discord", method = FormMethod.post) {
                h5("mt-5") {
                    +"Discord"
                }
                hr("mt-2") {}
                div("mb-3 d-grid") {
                    if (props.userDetail.type == AccountType.SIMPLE) {
                        // Can't use form as redirect to external site triggers CSP
                        a(classes = "btn btn-info", href = "/discord-link") {
                            +"Link discord"
                        }
                    } else {
                        button(classes = "btn btn-info", type = ButtonType.submit) {
                            attrs.disabled = state.loading == true
                            +"Unlink discord"
                        }
                    }
                }
            }
        }

        form(classes = "user-form", action = "/profile/unlink-patreon", method = FormMethod.post) {
            h5("mt-5") {
                +"Patreon"
            }
            hr("mt-2") {}
            div("mb-3 d-grid") {
                if (props.userDetail.patreon == null) {
                    // Can't use form as redirect to external site triggers CSP
                    a(classes = "btn btn-patreon", href = "/patreon") {
                        +"Link patreon"
                    }
                } else {
                    button(classes = "btn btn-patreon", type = ButtonType.submit) {
                        attrs.disabled = state.loading == true
                        +"Unlink patreon"
                    }
                }
            }
        }

        recaptcha(captchaRef)
    }
}

fun RBuilder.account(handler: AccountComponentProps.() -> Unit) =
    child(AccountComponent::class) {
        this.attrs(handler)
    }
