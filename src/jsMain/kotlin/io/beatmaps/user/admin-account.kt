package io.beatmaps.user

import external.Axios
import external.axiosDelete
import external.generateConfig
import external.reactFor
import io.beatmaps.Config
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.SessionRevokeRequest
import io.beatmaps.api.UserAdminRequest
import io.beatmaps.api.UserDetail
import io.beatmaps.api.UserSuspendRequest
import io.beatmaps.index.ModalButton
import io.beatmaps.index.ModalData
import io.beatmaps.index.modalContext
import io.beatmaps.shared.form.errors
import io.beatmaps.shared.form.toggle
import kotlinx.html.ButtonType
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.HTMLTextAreaElement
import react.Props
import react.dom.a
import react.dom.button
import react.dom.div
import react.dom.h5
import react.dom.hr
import react.dom.jsStyle
import react.dom.label
import react.dom.option
import react.dom.p
import react.dom.select
import react.dom.textarea
import react.fc
import react.useContext
import react.useEffectOnce
import react.useRef
import react.useState

external interface AdminAccountComponentProps : Props {
    var userDetail: UserDetail
    var onUpdate: () -> Unit
}

val adminAccount = fc<AdminAccountComponentProps> { props ->
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

    div(classes = "user-form") {
        h5("mt-5") {
            +"Admin"
        }
        hr("mt-2") {}
        div("mb-3") {
            if (success) {
                div("valid-feedback") {
                    attrs.jsStyle {
                        display = "block"
                    }
                    +"Updated successfully."
                }
            }

            label("form-label") {
                attrs.reactFor = "name"
                +"Max upload size"
            }
            select("form-select") {
                UserAdminRequest.allowedUploadSizes.forEach {
                    option {
                        +"$it"
                    }
                }

                attrs.value = uploadLimit.toString()
                attrs.onChangeFunction = {
                    setUploadLimit(maxUploadRef.current?.value?.toInt() ?: 15)
                }
                ref = maxUploadRef
            }
            toggle {
                key = "curator"
                ref = curatorRef
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
                ref = seniorCuratorRef
                attrs.id = "senior-curator"
                attrs.disabled = loading || !curator
                attrs.className = "mb-3 mt-3"
                attrs.text = "Senior Curator"
            }
            toggle {
                key = "curator-tab"
                ref = curatorTabRef
                attrs.id = "curator-tab"
                attrs.disabled = loading
                attrs.className = "mb-3 mt-3"
                attrs.text = "Curator tab"
            }
            toggle {
                key = "verifiedMapper"
                ref = verifiedMapperRef
                attrs.id = "verifiedMapper"
                attrs.disabled = loading
                attrs.className = "mb-3 mt-3"
                attrs.text = "Verified Mapper"
            }
            errors {
                attrs.errors = errors
            }
            div("d-grid") {
                button(classes = "btn btn-success", type = ButtonType.submit) {
                    attrs.onClickFunction = { ev ->
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
                    a("#", classes = "btn btn-info mt-2") {
                        attrs.onClickFunction = { ev ->
                            ev.preventDefault()

                            setLoading(true)
                            suspend(false)
                        }
                        +"Revoke Suspension"
                    }
                } else {
                    a("#", classes = "btn btn-danger mt-2") {
                        attrs.onClickFunction = { ev ->
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
                                        textarea(classes = "form-control") {
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
                a("#", classes = "btn btn-purple mt-2") {
                    attrs.onClickFunction = { ev ->
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
                                    textarea(classes = "form-control") {
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
