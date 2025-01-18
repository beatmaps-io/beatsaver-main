package io.beatmaps.admin.user

import external.Axios
import external.axiosDelete
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.admin.AdminAccountComponentProps
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.SessionRevokeRequest
import io.beatmaps.api.UserAdminRequest
import io.beatmaps.api.UserSuspendRequest
import io.beatmaps.shared.ModalButton
import io.beatmaps.shared.ModalData
import io.beatmaps.shared.form.errors
import io.beatmaps.shared.form.toggle
import io.beatmaps.shared.modalContext
import io.beatmaps.util.fcmemo
import js.objects.jso
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h5
import react.dom.html.ReactHTML.hr
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.option
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.select
import react.dom.html.ReactHTML.textarea
import react.use
import react.useEffectOnce
import react.useRef
import react.useState
import web.cssom.ClassName
import web.cssom.Display
import web.html.ButtonType
import web.html.HTMLInputElement
import web.html.HTMLSelectElement
import web.html.HTMLTextAreaElement

val adminAccount = fcmemo<AdminAccountComponentProps>("adminAccount") { props ->
    val maxUploadRef = useRef<HTMLSelectElement>()
    val curatorRef = useRef<HTMLInputElement>()
    val seniorCuratorRef = useRef<HTMLInputElement>()
    val curatorTabRef = useRef<HTMLInputElement>()
    val verifiedMapperRef = useRef<HTMLInputElement>()
    val reasonRef = useRef<HTMLTextAreaElement>()

    val (loading, setLoading) = useState(false)
    val (curator, setCurator) = useState(props.userDetail.curator == true)
    val (success, setSuccess) = useState(false)
    val (errors, setErrors) = useState(emptyList<String>())
    val (uploadLimit, setUploadLimit) = useState(props.userDetail.uploadLimit ?: 1)

    val modal = use(modalContext)

    useEffectOnce {
        curatorRef.current?.checked = props.userDetail.curator == true
        seniorCuratorRef.current?.checked = props.userDetail.seniorCurator == true
        curatorTabRef.current?.checked = props.userDetail.curatorTab
        verifiedMapperRef.current?.checked = props.userDetail.verifiedMapper
    }

    fun suspend(suspended: Boolean, reason: String? = null) =
        Axios.post<ActionResponse>(
            "${Config.apibase}/users/suspend",
            UserSuspendRequest(props.userDetail.id, suspended, reason),
            generateConfig<UserSuspendRequest, ActionResponse>()
        ).then {
            props.onUpdate()
            setErrors(it.data.errors)
            setLoading(false)
            setSuccess(it.data.success)
            true
        }.catch {
            // Cancelled request
            setLoading(false)
            setSuccess(false)
            false
        }

    fun revoke(reason: String? = null) =
        axiosDelete<SessionRevokeRequest, ActionResponse>(
            "${Config.apibase}/users/sessions",
            SessionRevokeRequest(userId = props.userDetail.id, site = true, reason = reason)
        ).then {
            setErrors(it.data.errors)
            setLoading(false)
            setSuccess(it.data.success)
            false
        }.catch {
            // Cancelled request
            setLoading(false)
            setSuccess(false)
            true
        }

    div {
        className = ClassName("user-form")
        h5 {
            className = ClassName("mt-5")
            +"Admin"
        }
        hr {
            className = ClassName("mt-2")
        }
        div {
            className = ClassName("mb-3")
            if (success) {
                div {
                    className = ClassName("valid-feedback")
                    style = jso {
                        display = Display.block
                    }
                    +"Updated successfully."
                }
            }

            label {
                className = ClassName("form-label")
                htmlFor = "name"
                +"Max upload size"
            }
            select {
                className = ClassName("form-select")
                UserAdminRequest.allowedUploadSizes.forEach {
                    option {
                        +"$it"
                    }
                }

                value = uploadLimit.toString()
                onChange = {
                    setUploadLimit(maxUploadRef.current?.value?.toInt() ?: 15)
                }
                ref = maxUploadRef
            }
            toggle {
                key = "curator"
                toggleRef = curatorRef
                id = "curator"
                disabled = loading
                block = {
                    setCurator(it)
                    seniorCuratorRef.current?.apply { checked = checked && it }
                }
                className = "mb-3 mt-3"
                text = "Curator"
            }
            toggle {
                key = "senior-curator"
                toggleRef = seniorCuratorRef
                id = "senior-curator"
                disabled = loading || !curator
                className = "mb-3 mt-3"
                text = "Senior Curator"
            }
            toggle {
                key = "curator-tab"
                toggleRef = curatorTabRef
                id = "curator-tab"
                disabled = loading
                className = "mb-3 mt-3"
                text = "Curator tab"
            }
            toggle {
                key = "verifiedMapper"
                toggleRef = verifiedMapperRef
                id = "verifiedMapper"
                disabled = loading
                className = "mb-3 mt-3"
                text = "Verified Mapper"
            }
            errors {
                this.errors = errors
            }
            div {
                className = ClassName("d-grid")
                button {
                    className = ClassName("btn btn-success")
                    type = ButtonType.submit
                    onClick = { ev ->
                        ev.preventDefault()

                        setLoading(true)

                        Axios.post<ActionResponse>(
                            "${Config.apibase}/users/admin",
                            UserAdminRequest(
                                props.userDetail.id,
                                uploadLimit,
                                curatorRef.current?.checked ?: false,
                                seniorCuratorRef.current?.checked ?: false,
                                curatorTabRef.current?.checked ?: false,
                                verifiedMapperRef.current?.checked ?: false
                            ),
                            generateConfig<UserAdminRequest, ActionResponse>()
                        ).then {
                            props.onUpdate()
                            setErrors(it.data.errors)
                            setLoading(false)
                            setSuccess(it.data.success)
                        }.catch {
                            // Cancelled request
                            setLoading(false)
                            setSuccess(false)
                        }
                    }
                    disabled = loading
                    +"Save"
                }
                if (props.userDetail.suspendedAt != null) {
                    a {
                        href = "#"
                        className = ClassName("btn btn-info mt-2")
                        onClick = { ev ->
                            ev.preventDefault()

                            setLoading(true)
                            suspend(false)
                        }
                        +"Revoke Suspension"
                    }
                } else {
                    a {
                        href = "#"
                        className = ClassName("btn btn-danger mt-2")
                        onClick = { ev ->
                            ev.preventDefault()
                            setLoading(true)

                            modal?.current?.showDialog?.invoke(
                                ModalData(
                                    "Suspend user",
                                    bodyCallback = {
                                        p {
                                            +"Suspend this user so they can't upload maps, publish playlists, or post reviews?"
                                        }
                                        p {
                                            +"Reason for action:"
                                        }
                                        textarea {
                                            className = ClassName("form-control")
                                            ref = reasonRef
                                        }
                                    },
                                    buttons = listOf(
                                        ModalButton("Suspend", "primary") { suspend(true, reasonRef.current?.value) },
                                        ModalButton("Cancel")
                                    )
                                )
                            )
                        }
                        +"Suspend"
                    }
                }
                a {
                    href = "#"
                    className = ClassName("btn btn-purple mt-2")
                    onClick = { ev ->
                        ev.preventDefault()
                        setLoading(true)

                        modal?.current?.showDialog?.invoke(
                            ModalData(
                                "Revoke logins",
                                bodyCallback = {
                                    p {
                                        +"Revoke all devices this user is currently logged in on?"
                                    }
                                    p {
                                        +"Reason for action:"
                                    }
                                    textarea {
                                        className = ClassName("form-control")
                                        ref = reasonRef
                                    }
                                },
                                buttons = listOf(ModalButton("Revoke", "primary") { revoke(reasonRef.current?.value) }, ModalButton("Cancel"))
                            )
                        )
                    }
                    +"Revoke Logins"
                }
            }
        }
    }
}
