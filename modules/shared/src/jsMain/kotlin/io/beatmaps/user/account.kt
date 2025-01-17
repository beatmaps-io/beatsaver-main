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
import io.beatmaps.util.form
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.files.get
import org.w3c.xhr.FormData
import react.Props
import react.dom.FormAction
import react.dom.aria.AriaRole
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.form
import react.dom.html.ReactHTML.h5
import react.dom.html.ReactHTML.hr
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.fc
import react.useCallback
import react.useEffect
import react.useRef
import react.useState
import web.cssom.Auto.Companion.auto
import web.cssom.ClassName
import web.form.FormMethod
import web.html.ButtonType
import web.html.InputType
import kotlin.js.Promise

external interface AccountComponentProps : Props {
    var userDetail: UserDetail
    var onUpdate: () -> Unit
}

val accountTab = fc<AccountComponentProps>("accountTab") { props ->
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
        attrs.className = ClassName("user-form")
        h5 {
            attrs.className = ClassName("mt-5")
            +"Account details"
        }
        hr {
            attrs.className = ClassName("mt-2")
        }
        if (props.userDetail.type != AccountType.DISCORD) {
            accountEmail {
                attrs.userDetail = props.userDetail
                attrs.captchaRef = captchaRef
            }
        }
        div {
            attrs.className = ClassName("mb-3")
            attrs.id = "change-username"
            errors {
                attrs.errors = usernameErrors
            }
            label {
                attrs.className = ClassName("form-label")
                attrs.htmlFor = "name"
                +"Username"
            }
            input {
                key = "username"
                attrs.type = InputType.text
                attrs.className = ClassName("form-control")
                attrs.id = "name"
                attrs.value = username
                attrs.onChange = {
                    setUsername(it.target.value)
                }
            }
            div {
                attrs.className = ClassName("d-grid")
                button {
                    attrs.className = ClassName("btn btn-success")
                    attrs.type = ButtonType.submit
                    attrs.onClick = { ev ->
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
                    attrs.disabled = userLoading
                    +"Change username"
                }
            }
        }
        errors {
            attrs.errors = descriptionErrors
        }
        div {
            attrs.className = ClassName("mb-3")
            label {
                attrs.className = ClassName("form-label")
                attrs.htmlFor = "description"
                +"Description"
            }
            editableText {
                attrs.editing = true
                attrs.rows = 5
                attrs.btnClass = "btn-success"
                attrs.flex = auto
                attrs.text = props.userDetail.description ?: ""
                attrs.buttonText = "Change description"
                attrs.maxLength = UserConstants.MAX_DESCRIPTION_LENGTH
                attrs.onError = {
                    setDescriptionErrors(it)
                }
                attrs.stopEditing = {
                    props.onUpdate()
                }
                attrs.saveText = { newDescription ->
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
            attrs.className = ClassName("user-form")
            h5 {
                attrs.className = ClassName("mt-5")
                +"Avatar"
            }
            hr {
                attrs.className = ClassName("mt-2")
            }
            div {
                attrs.className = ClassName("mb-3")
                input {
                    key = "avatar"
                    ref = avatarRef
                    attrs.type = InputType.file
                    attrs.hidden = uploading == true
                }
                div {
                    attrs.className = ClassName("d-grid")
                    button {
                        attrs.type = ButtonType.submit
                        attrs.className = ClassName("btn btn-success")
                        attrs.hidden = uploading == true
                        attrs.onClick = { ev ->
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
                    attrs.className = ClassName("progress")
                    attrs.hidden = uploading == false
                    div {
                        attrs.className = ClassName("progress-bar progress-bar-striped progress-bar-animated bg-info")
                        attrs.role = AriaRole.progressbar
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
    form {
        attrs.className = ClassName("user-form")
        attrs.onSubmit = { ev ->
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
            attrs.className = ClassName("mt-5")
            +"NSFW Content"
        }
        hr {
            attrs.className = ClassName("mt-2")
        }
        div {
            attrs.className = ClassName("mb-3 d-grid")
            val cb = useCallback { it: Boolean ->
                setBlur(it)
            }
            toggle {
                key = "nsfw"
                attrs.id = "nsfw"
                attrs.disabled = blurLoading
                attrs.block = cb
                attrs.text = "Hide content"
                attrs.default = props.userDetail.blurnsfw
            }
        }
        div {
            attrs.className = ClassName("d-grid")
            button {
                attrs.className = ClassName("btn btn-success")
                attrs.type = ButtonType.submit
                attrs.disabled = blurLoading == true
                +"Save"
            }
        }
    }
    if (props.userDetail.type != AccountType.DISCORD) {
        form("user-form", FormMethod.post, "/profile/unlink-discord") {
            h5 {
                attrs.className = ClassName("mt-5")
                +"Discord"
            }
            hr {
                attrs.className = ClassName("mt-2")
            }
            div {
                attrs.className = ClassName("mb-3 d-grid")
                if (props.userDetail.type == AccountType.SIMPLE) {
                    // Can't use form as redirect to external site triggers CSP
                    a {
                        attrs.className = ClassName("btn btn-info")
                        attrs.href = "/discord-link"
                        +"Link discord"
                    }
                } else {
                    button {
                        attrs.className = ClassName("btn btn-info")
                        attrs.type = ButtonType.submit
                        +"Unlink discord"
                    }
                }
            }
        }
    }

    form("user-form", FormMethod.post, "/profile/unlink-patreon") {
        h5 {
            attrs.className = ClassName("mt-5")
            +"Patreon"
        }
        hr {
            attrs.className = ClassName("mt-2")
        }
        div {
            attrs.className = ClassName("mb-3 d-grid")
            if (props.userDetail.patreon == null) {
                // Can't use form as redirect to external site triggers CSP
                a {
                    attrs.className = ClassName("btn btn-patreon")
                    attrs.href = "/patreon"
                    +"Link patreon"
                }
            } else {
                button {
                    attrs.className = ClassName("btn btn-patreon")
                    attrs.type = ButtonType.submit
                    +"Unlink patreon"
                }
            }
        }
    }

    captcha {
        key = "captcha"
        attrs.captchaRef = captchaRef
        attrs.page = "account"
    }
}
