package io.beatmaps.user

import external.Axios
import external.generateConfig
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.UserAdminRequest
import io.beatmaps.api.UserDetail
import kotlinx.html.ButtonType
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLSelectElement
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
import react.createRef
import react.dom.button
import react.dom.div
import react.dom.h5
import react.dom.hr
import react.dom.label
import react.dom.option
import react.dom.select
import react.setState

external interface AdminAccountComponentProps : RProps {
    var userDetail: UserDetail
}

external interface AdminAccountComponentState : RState {
    var loading: Boolean?
    var errors: List<String>
}

@JsExport
class AdminAccountComponent : RComponent<AdminAccountComponentProps, AdminAccountComponentState>() {
    private val maxUploadRef = createRef<HTMLSelectElement>()

    override fun componentWillMount() {
        setState {
            loading = false
            errors = listOf()
        }
    }

    override fun RBuilder.render() {
        div(classes = "user-form") {
            h5("mt-5") {
                +"Admin"
            }
            hr("mt-2") {}
            div("form-group") {
                label {
                    attrs.htmlFor = "name"
                    +"Max upload size"
                }
                select("form-control") {
                    arrayOf(0, 15).forEach {
                        option {
                            +"$it"
                        }
                    }

                    ref = maxUploadRef
                }
                button(classes = "btn btn-success btn-block", type = ButtonType.submit) {
                    attrs.onClickFunction = { ev ->
                        ev.preventDefault()

                        val newMaxUpload = maxUploadRef.current?.value?.toInt() ?: error("Error getting max upload value")

                        setState {
                            loading = true
                        }

                        Axios.post<ActionResponse>(
                            "/api/users/admin",
                            UserAdminRequest(props.userDetail.id, newMaxUpload),
                            generateConfig<UserAdminRequest, ActionResponse>()
                        ).then {
                            setState {
                                errors = it.data.errors
                                loading = false
                            }
                        }.catch {
                            // Cancelled request
                            setState {
                                loading = false
                            }
                        }
                    }
                    attrs.disabled = state.loading == true
                    +"Save"
                }
            }
        }
    }
}

fun RBuilder.adminAccount(handler: AdminAccountComponentProps.() -> Unit): ReactElement {
    return child(AdminAccountComponent::class) {
        this.attrs(handler)
    }
}
