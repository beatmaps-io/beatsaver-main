package io.beatmaps.user

import external.Axios
import external.generateConfig
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.UserAdminRequest
import io.beatmaps.api.UserDetail
import io.beatmaps.common.Config
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.id
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLInputElement
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
import react.dom.input
import react.dom.jsStyle
import react.dom.label
import react.dom.option
import react.dom.select
import react.setState

external interface AdminAccountComponentProps : RProps {
    var userDetail: UserDetail
}

external interface AdminAccountComponentState : RState {
    var loading: Boolean?
    var success: Boolean?
    var errors: List<String>
    var uploadLimit: Int
}

@JsExport
class AdminAccountComponent : RComponent<AdminAccountComponentProps, AdminAccountComponentState>() {
    private val maxUploadRef = createRef<HTMLSelectElement>()
    private val curatorRef = createRef<HTMLInputElement>()
    private val verifiedMapperRef = createRef<HTMLInputElement>()

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
                    attrs.htmlFor = "name"
                    +"Max upload size"
                }
                select("form-select") {
                    arrayOf(0, 15, 30).forEach {
                        option {
                            attrs.selected = state.uploadLimit == it
                            +"$it"
                        }
                    }

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
                        attrs.htmlFor = "curator"
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
                        attrs.htmlFor = "verifiedMapper"
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
