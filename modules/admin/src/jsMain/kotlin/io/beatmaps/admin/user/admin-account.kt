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
import js.objects.jso
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.HTMLTextAreaElement
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
import react.fc
import react.useContext
import react.useEffectOnce
import react.useRef
import react.useState
import web.cssom.ClassName
import web.cssom.Display
import web.html.ButtonType

val adminAccount = fc<AdminAccountComponentProps>("adminAccount") { props ->
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

    val modal = useContext(modalContext)

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
        attrs.className = ClassName("user-form")
        h5 {
            attrs.className = ClassName("mt-5")
            +"Admin"
        }
        hr {
            attrs.className = ClassName("mt-2")
        }
        div {
            attrs.className = ClassName("mb-3")
            if (success) {
                div {
                    attrs.className = ClassName("valid-feedback")
                    attrs.style = jso {
                        display = Display.block
                    }
                    +"Updated successfully."
                }
            }

            label {
                attrs.className = ClassName("form-label")
                attrs.htmlFor = "name"
                +"Max upload size"
            }
            select {
                attrs.className = ClassName("form-select")
                UserAdminRequest.allowedUploadSizes.forEach {
                    option {
                        +"$it"
                    }
                }

                attrs.value = uploadLimit.toString()
                attrs.onChange = {
                    setUploadLimit(maxUploadRef.current?.value?.toInt() ?: 15)
                }
                ref = maxUploadRef
            }
            toggle {
                key = "curator"
                attrs.toggleRef = curatorRef
                attrs.id = "curator"
                attrs.disabled = loading
                attrs.block = {
                    setCurator(it)
                    seniorCuratorRef.current?.apply { checked = checked && it }
                }
                attrs.className = "mb-3 mt-3"
                attrs.text = "Curator"
            }
            toggle {
                key = "senior-curator"
                attrs.toggleRef = seniorCuratorRef
                attrs.id = "senior-curator"
                attrs.disabled = loading || !curator
                attrs.className = "mb-3 mt-3"
                attrs.text = "Senior Curator"
            }
            toggle {
                key = "curator-tab"
                attrs.toggleRef = curatorTabRef
                attrs.id = "curator-tab"
                attrs.disabled = loading
                attrs.className = "mb-3 mt-3"
                attrs.text = "Curator tab"
            }
            toggle {
                key = "verifiedMapper"
                attrs.toggleRef = verifiedMapperRef
                attrs.id = "verifiedMapper"
                attrs.disabled = loading
                attrs.className = "mb-3 mt-3"
                attrs.text = "Verified Mapper"
            }
            errors {
                attrs.errors = errors
            }
            div {
                attrs.className = ClassName("d-grid")
                button {
                    attrs.className = ClassName("btn btn-success")
                    attrs.type = ButtonType.submit
                    attrs.onClick = { ev ->
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
                    attrs.disabled = loading
                    +"Save"
                }
                if (props.userDetail.suspendedAt != null) {
                    a {
                        attrs.href = "#"
                        attrs.className = ClassName("btn btn-info mt-2")
                        attrs.onClick = { ev ->
                            ev.preventDefault()

                            setLoading(true)
                            suspend(false)
                        }
                        +"Revoke Suspension"
                    }
                } else {
                    a {
                        attrs.href = "#"
                        attrs.className = ClassName("btn btn-danger mt-2")
                        attrs.onClick = { ev ->
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
                                            attrs.className = ClassName("form-control")
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
                    attrs.href = "#"
                    attrs.className = ClassName("btn btn-purple mt-2")
                    attrs.onClick = { ev ->
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
                                        attrs.className = ClassName("form-control")
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
