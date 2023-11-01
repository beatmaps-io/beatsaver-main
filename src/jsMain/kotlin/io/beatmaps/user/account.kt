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
import io.beatmaps.modreview.editableText
import io.beatmaps.shared.form.errors
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
import org.w3c.files.get
import org.w3c.xhr.FormData
import react.Props
import react.dom.a
import react.dom.button
import react.dom.div
import react.dom.form
import react.dom.h5
import react.dom.hr
import react.dom.input
import react.dom.label
import react.fc
import react.useEffect
import react.useRef
import react.useState
import kotlin.js.Promise

external interface AccountComponentProps : Props {
    var userDetail: UserDetail
    var onUpdate: () -> Unit
}

val accountTab = fc<AccountComponentProps> { props ->
    val (username, setUsername) = useState(props.userDetail.name)
    val (usernameErrors, setUsernameErrors) = useState(listOf<String>())
    val (userLoading, setUserLoading) = useState(false)

    val (descriptionErrors, setDescriptionErrors) = useState(listOf<String>())

    val (uploading, setUploading) = useState(false)
    val avatarRef = useRef<HTMLInputElement>()
    val progressBarInnerRef = useRef<HTMLElement>()

    val (sessions, setSessions) = useState<SessionsData?>(null)

    val captchaRef = useRef<ReCAPTCHA>()

    useEffect(props.userDetail) {
        axiosGet<SessionsData>(
            "${Config.apibase}/users/sessions"
        ).then {
            setSessions(it.data)
        }.catch {
            // Ignore
        }
    }

    fun removeSessionCallback(session: SessionInfo) {
        setSessions(
            SessionsData(
                sessions?.oauth?.filter { c -> c.id != session.id } ?: listOf(),
                sessions?.site?.filter { c -> c.id != session.id } ?: listOf()
            )
        )
    }

    fun revokeAll() {
        axiosDelete<SessionRevokeRequest, String>("${Config.apibase}/users/sessions", SessionRevokeRequest(site = true)).then {
            setSessions(SessionsData(listOf(), sessions?.site?.filter { c -> c.current } ?: listOf()))
        }
    }

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
            attrs.errors = usernameErrors
        }
        div("mb-3") {
            label("form-label") {
                attrs.reactFor = "name"
                +"Username"
            }
            input(InputType.text, classes = "form-control") {
                key = "username"
                attrs.id = "name"
                attrs.value = username
                attrs.onChangeFunction = {
                    setUsername((it.target as HTMLInputElement).value)
                }
            }
            div("d-grid") {
                button(classes = "btn btn-success", type = ButtonType.submit) {
                    attrs.onClickFunction = { ev ->
                        ev.preventDefault()

                        if (props.userDetail.name == username) {
                            setUsernameErrors(listOf("That's already your username!"))
                        } else {
                            setUserLoading(true)

                            Axios.post<ActionResponse>(
                                "${Config.apibase}/users/username",
                                AccountDetailReq(username),
                                generateConfig<AccountDetailReq, ActionResponse>()
                            ).then {
                                if (it.data.success) {
                                    props.onUpdate()
                                    setUserLoading(false)
                                } else {
                                    setUsernameErrors(it.data.errors)
                                    setUserLoading(false)
                                }
                            }.catch {
                                // Cancelled request
                                setUserLoading(false)
                            }
                        }
                    }
                    attrs.disabled = userLoading
                    +"Change username"
                }
            }
        }
        errors {
            attrs.errors = descriptionErrors
        }
        div("mb-3") {
            label("form-label") {
                attrs.reactFor = "description"
                +"Description"
            }
            editableText {
                attrs.editing = true
                attrs.rows = 5
                attrs.btnClass = "btn-success"
                attrs.justify = "stretch"
                attrs.text = props.userDetail.description ?: ""
                attrs.buttonText = "Change description"
                attrs.maxLength = 500
                attrs.saveText = { newDescription ->
                    if (props.userDetail.description != newDescription) {
                        Axios.post<ActionResponse>(
                            "${Config.apibase}/users/description",
                            AccountDetailReq(newDescription),
                            generateConfig<AccountDetailReq, ActionResponse>()
                        ).then {
                            if (it.data.success) {
                                props.onUpdate()
                            } else {
                                setDescriptionErrors(it.data.errors)
                            }

                            it
                        }
                    } else {
                        Promise.reject(IllegalStateException("Description unchanged"))
                    }
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
                    attrs.hidden = uploading == true
                }
                div("d-grid") {
                    button(classes = "btn btn-success", type = ButtonType.submit) {
                        attrs.hidden = uploading == true
                        attrs.onClickFunction = { ev ->
                            ev.preventDefault()

                            val file = avatarRef.current?.files?.let { it[0] }
                            if (file != null) {
                                setUploading(true)

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
                                        setUploading(false)
                                    } else {
                                        setUploading(false)
                                        avatarRef.current?.value = ""
                                    }
                                }.catch {
                                    setUploading(false)
                                    avatarRef.current?.value = ""
                                }
                            }
                        }
                        +"Upload"
                    }
                }
                div("progress") {
                    attrs.hidden = uploading == false
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
    sessions?.let { s ->
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
                    +"Unlink patreon"
                }
            }
        }
    }

    recaptcha(captchaRef)
}
