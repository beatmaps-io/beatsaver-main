package io.beatmaps.user

import external.Axios
import external.axiosDelete
import external.axiosGet
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.AccountDetailReq
import io.beatmaps.api.AccountType
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.BlurReq
import io.beatmaps.api.SessionInfo
import io.beatmaps.api.SessionRevokeRequest
import io.beatmaps.api.SessionsData
import io.beatmaps.api.UploadResponse
import io.beatmaps.api.UserConstants
import io.beatmaps.api.UserDetail
import io.beatmaps.captcha.ICaptchaHandler
import io.beatmaps.captcha.captcha
import io.beatmaps.shared.editableText
import io.beatmaps.shared.form.errors
import io.beatmaps.shared.form.toggle
import io.beatmaps.upload.UploadRequestConfig
import io.beatmaps.user.account.accountEmail
import io.beatmaps.user.account.changePassword
import io.beatmaps.user.account.manageSessions
import io.beatmaps.util.fcmemo
import io.beatmaps.util.form
import react.Props
import react.dom.aria.AriaRole
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.form
import react.dom.html.ReactHTML.h5
import react.dom.html.ReactHTML.hr
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.useCallback
import react.useEffect
import react.useRef
import react.useState
import web.cssom.Auto.Companion.auto
import web.cssom.ClassName
import web.form.FormData
import web.form.FormMethod
import web.html.ButtonType
import web.html.HTMLElement
import web.html.HTMLInputElement
import web.html.InputType
import kotlin.js.Promise

external interface AccountComponentProps : Props {
    var userDetail: UserDetail
    var onUpdate: () -> Unit
}

val accountTab = fcmemo<AccountComponentProps>("accountTab") { props ->
    val (username, setUsername) = useState(props.userDetail.name)
    val (usernameErrors, setUsernameErrors) = useState(emptyList<String>())
    val (userLoading, setUserLoading) = useState(false)
    val (blurLoading, setBlurLoading) = useState(false)
    val (blur, setBlur) = useState(props.userDetail.blurnsfw ?: true)

    val (descriptionErrors, setDescriptionErrors) = useState(emptyList<String>())

    val (uploading, setUploading) = useState(false)
    val avatarRef = useRef<HTMLInputElement>()
    val progressBarInnerRef = useRef<HTMLElement>()

    val (sessions, setSessions) = useState<SessionsData?>(null)

    val captchaRef = useRef<ICaptchaHandler>()

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

    fun revokeAll() =
        axiosDelete<SessionRevokeRequest, String>("${Config.apibase}/users/sessions", SessionRevokeRequest(site = true)).then({
            setSessions(SessionsData(sessions?.oauth ?: listOf(), sessions?.site?.filter { c -> c.current } ?: listOf()))
            true
        }) { false }

    // Having 2 forms confuses password managers, only the password section needs to invoke them
    div {
        className = ClassName("user-form")
        h5 {
            className = ClassName("mt-5")
            +"Account details"
        }
        hr {
            className = ClassName("mt-2")
        }
        if (props.userDetail.type != AccountType.DISCORD) {
            accountEmail {
                userDetail = props.userDetail
                this.captchaRef = captchaRef
            }
        }
        div {
            className = ClassName("mb-3")
            id = "change-username"
            errors {
                errors = usernameErrors
            }
            label {
                className = ClassName("form-label")
                htmlFor = "name"
                +"Username"
            }
            input {
                key = "username"
                type = InputType.text
                className = ClassName("form-control")
                id = "name"
                value = username
                onChange = {
                    setUsername(it.target.value)
                }
            }
            div {
                className = ClassName("d-grid")
                button {
                    className = ClassName("btn btn-success")
                    type = ButtonType.submit
                    onClick = { ev ->
                        ev.preventDefault()

                        if (props.userDetail.name == username) {
                            setUsernameErrors(listOf("That's already your username!"))
                        } else {
                            setUserLoading(true)

                            Axios.post<ActionResponse>(
                                "${Config.apibase}/users/username",
                                AccountDetailReq(username),
                                generateConfig<AccountDetailReq, ActionResponse>(validStatus = arrayOf(200, 400))
                            ).then {
                                if (it.data.success) {
                                    props.onUpdate()
                                } else {
                                    setUsernameErrors(it.data.errors)
                                }
                            }.finally {
                                setUserLoading(false)
                            }
                        }
                    }
                    disabled = userLoading
                    +"Change username"
                }
            }
        }
        errors {
            errors = descriptionErrors
        }
        div {
            className = ClassName("mb-3")
            label {
                className = ClassName("form-label")
                htmlFor = "description"
                +"Description"
            }
            editableText {
                editing = true
                rows = 5
                btnClass = "btn-success"
                flex = auto
                text = props.userDetail.description ?: ""
                buttonText = "Change description"
                maxLength = UserConstants.MAX_DESCRIPTION_LENGTH
                onError = {
                    setDescriptionErrors(it)
                }
                stopEditing = {
                    props.onUpdate()
                }
                saveText = { newDescription ->
                    if (props.userDetail.description != newDescription) {
                        Axios.post(
                            "${Config.apibase}/users/description",
                            AccountDetailReq(newDescription),
                            generateConfig<AccountDetailReq, ActionResponse>()
                        )
                    } else {
                        Promise.reject(IllegalStateException("Description unchanged"))
                    }
                }
            }
        }
    }
    if (props.userDetail.type == AccountType.SIMPLE) {
        div {
            className = ClassName("user-form")
            h5 {
                className = ClassName("mt-5")
                +"Avatar"
            }
            hr {
                className = ClassName("mt-2")
            }
            div {
                className = ClassName("mb-3")
                input {
                    key = "avatar"
                    ref = avatarRef
                    type = InputType.file
                    hidden = uploading == true
                }
                div {
                    className = ClassName("d-grid")
                    button {
                        type = ButtonType.submit
                        className = ClassName("btn btn-success")
                        hidden = uploading == true
                        onClick = { ev ->
                            ev.preventDefault()

                            val file = avatarRef.current?.files?.let { it[0] }
                            if (file != null) {
                                setUploading(true)

                                val data = FormData()
                                data.append("file", file)

                                Axios.post<UploadResponse>(
                                    "/avatar", data,
                                    UploadRequestConfig { progress ->
                                        val v = ((progress.loaded * 100f) / progress.total).toInt()
                                        progressBarInnerRef.current?.style?.width = "$v%"
                                    }
                                ).then({ r ->
                                    if (r.status == 200) {
                                        props.onUpdate()
                                    } else {
                                        avatarRef.current?.value = ""
                                    }
                                }) {
                                    avatarRef.current?.value = ""
                                }.finally {
                                    setUploading(false)
                                }
                            }
                        }
                        +"Upload"
                    }
                }
                div {
                    className = ClassName("progress")
                    hidden = uploading == false
                    div {
                        className = ClassName("progress-bar progress-bar-striped progress-bar-animated bg-info")
                        role = AriaRole.progressbar
                        ref = progressBarInnerRef
                    }
                }
            }
        }
    }
    if (props.userDetail.type != AccountType.DISCORD) {
        changePassword {
            userDetail = props.userDetail
        }
    }
    sessions?.let { s ->
        manageSessions {
            revokeAllCallback = ::revokeAll
            removeSessionCallback = ::removeSessionCallback
            this.sessions = s
        }
    }
    form {
        className = ClassName("user-form")
        onSubmit = { ev ->
            ev.preventDefault()
            setBlurLoading(true)
            Axios.post<ActionResponse>(
                "${Config.apibase}/users/blur",
                BlurReq(blur),
                generateConfig<BlurReq, ActionResponse>()
            ).then {
                // Do nothing :)
            }.finally {
                setBlurLoading(false)
            }
        }
        h5 {
            className = ClassName("mt-5")
            +"NSFW Content"
        }
        hr {
            className = ClassName("mt-2")
        }
        div {
            className = ClassName("mb-3 d-grid")
            val cb = useCallback { it: Boolean ->
                setBlur(it)
            }
            toggle {
                key = "nsfw"
                id = "nsfw"
                disabled = blurLoading
                block = cb
                text = "Hide content"
                default = props.userDetail.blurnsfw
            }
        }
        div {
            className = ClassName("d-grid")
            button {
                className = ClassName("btn btn-success")
                type = ButtonType.submit
                disabled = blurLoading == true
                +"Save"
            }
        }
    }
    if (props.userDetail.type != AccountType.DISCORD) {
        form("user-form", FormMethod.post, "/profile/unlink-discord") {
            h5 {
                className = ClassName("mt-5")
                +"Discord"
            }
            hr {
                className = ClassName("mt-2")
            }
            div {
                className = ClassName("mb-3 d-grid")
                if (props.userDetail.type == AccountType.SIMPLE) {
                    // Can't use form as redirect to external site triggers CSP
                    a {
                        className = ClassName("btn btn-info")
                        href = "/discord-link"
                        +"Link discord"
                    }
                } else {
                    button {
                        className = ClassName("btn btn-info")
                        type = ButtonType.submit
                        +"Unlink discord"
                    }
                }
            }
        }
    }

    form("user-form", FormMethod.post, "/profile/unlink-patreon") {
        h5 {
            className = ClassName("mt-5")
            +"Patreon"
        }
        hr {
            className = ClassName("mt-2")
        }
        div {
            className = ClassName("mb-3 d-grid")
            if (props.userDetail.patreon == null) {
                // Can't use form as redirect to external site triggers CSP
                a {
                    className = ClassName("btn btn-patreon")
                    href = "/patreon"
                    +"Link patreon"
                }
            } else {
                button {
                    className = ClassName("btn btn-patreon")
                    type = ButtonType.submit
                    +"Unlink patreon"
                }
            }
        }
    }

    captcha {
        key = "captcha"
        this.captchaRef = captchaRef
        page = "account"
    }
}
