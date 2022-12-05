package io.beatmaps.user

import external.Axios
import external.generateConfig
import external.reactFor
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.UserAdminRequest
import io.beatmaps.api.UserDetail
import io.beatmaps.api.UserSuspendRequest
import io.beatmaps.common.Config
import io.beatmaps.index.ModalButton
import io.beatmaps.index.ModalComponent
import io.beatmaps.index.ModalData
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.id
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.HTMLTextAreaElement
import react.Props
import react.RBuilder
import react.RComponent
import react.RefObject
import react.State
import react.createRef
import react.dom.a
import react.dom.button
import react.dom.div
import react.dom.h5
import react.dom.hr
import react.dom.input
import react.dom.jsStyle
import react.dom.label
import react.dom.option
import react.dom.p
import react.dom.select
import react.dom.textarea
import react.setState

external interface AdminAccountComponentProps : Props {
    var userDetail: UserDetail
    var modal: RefObject<ModalComponent>
    var onUpdate: () -> Unit
}

external interface AdminAccountComponentState : State {
    var loading: Boolean?
    var success: Boolean?
    var errors: List<String>
    var uploadLimit: Int
}

class AdminAccountComponent : RComponent<AdminAccountComponentProps, AdminAccountComponentState>() {
    private val maxUploadRef = createRef<HTMLSelectElement>()
    private val curatorRef = createRef<HTMLInputElement>()
    private val verifiedMapperRef = createRef<HTMLInputElement>()
    private val reasonRef = createRef<HTMLTextAreaElement>()

    override fun componentWillMount() {
        setState {
            loading = false
            success = false
            errors = listOf()
            uploadLimit = props.userDetail.uploadLimit ?: 1
        }
    }

    override fun componentDidMount() {
        curatorRef.current?.checked = props.userDetail.curator == true
        verifiedMapperRef.current?.checked = props.userDetail.verifiedMapper
    }

    private fun suspend(suspended: Boolean, reason: String? = null) {
        Axios.post<ActionResponse>(
            "${Config.apibase}/users/suspend",
            UserSuspendRequest(props.userDetail.id, suspended, reason),
            generateConfig<UserSuspendRequest, ActionResponse>()
        ).then {
            props.onUpdate()
            setState {
                errors = it.data.errors
                loading = false
                success = it.data.success
            }
        }.catch {
            // Cancelled request
            setState {
                loading = false
                success = false
            }
        }
    }

    override fun RBuilder.render() {
        div(classes = "user-form") {
            h5("mt-5") {
                +"Admin"
            }
            hr("mt-2") {}
            div("mb-3") {
                if (state.success == true) {
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
                    arrayOf(0, 15, 30).forEach {
                        option {
                            +"$it"
                        }
                    }

                    attrs.value = state.uploadLimit.toString()
                    attrs.onChangeFunction = {
                        setState {
                            uploadLimit = maxUploadRef.current?.value?.toInt() ?: 15
                        }
                    }
                    ref = maxUploadRef
                }
                div("form-check form-switch mb-3 mt-3") {
                    key = "curator"
                    input(InputType.checkBox, classes = "form-check-input") {
                        attrs.id = "curator"
                        attrs.disabled = state.loading == true
                        ref = curatorRef
                    }
                    label("form-check-label") {
                        attrs.reactFor = "curator"
                        +"Curator"
                    }
                }
                div("form-check form-switch mb-3 mt-3") {
                    key = "verifiedMapper"
                    input(InputType.checkBox, classes = "form-check-input") {
                        attrs.id = "verifiedMapper"
                        attrs.disabled = state.loading == true
                        ref = verifiedMapperRef
                    }
                    label("form-check-label") {
                        attrs.reactFor = "verifiedMapper"
                        +"Verified Mapper"
                    }
                }
                div("d-grid") {
                    button(classes = "btn btn-success", type = ButtonType.submit) {
                        attrs.onClickFunction = { ev ->
                            ev.preventDefault()

                            setState {
                                loading = true
                            }

                            Axios.post<ActionResponse>(
                                "${Config.apibase}/users/admin",
                                UserAdminRequest(props.userDetail.id, state.uploadLimit, curatorRef.current?.checked ?: false, verifiedMapperRef.current?.checked ?: false),
                                generateConfig<UserAdminRequest, ActionResponse>()
                            ).then {
                                props.onUpdate()
                                setState {
                                    errors = it.data.errors
                                    loading = false
                                    success = it.data.success
                                }
                            }.catch {
                                // Cancelled request
                                setState {
                                    loading = false
                                    success = false
                                }
                            }
                        }
                        attrs.disabled = state.loading == true
                        +"Save"
                    }
                    if (props.userDetail.suspendedAt != null) {
                        a("#", classes = "btn btn-info mt-2") {
                            attrs.onClickFunction = { ev ->
                                ev.preventDefault()

                                setState {
                                    loading = true
                                }

                                suspend(false)
                            }
                            +"Revoke Suspension"
                        }
                    } else {
                        a("#", classes = "btn btn-danger mt-2") {
                            attrs.onClickFunction = { ev ->
                                ev.preventDefault()

                                setState {
                                    loading = true
                                }

                                props.modal.current?.showDialog(
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
                }
            }
        }
    }
}

fun RBuilder.adminAccount(handler: AdminAccountComponentProps.() -> Unit) =
    child(AdminAccountComponent::class) {
        this.attrs(handler)
    }
