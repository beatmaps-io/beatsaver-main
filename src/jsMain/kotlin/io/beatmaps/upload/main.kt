package io.beatmaps.maps

import AxiosProgress
import AxiosRequestConfig
import external.Dropzone
import external.ReCAPTCHA
import io.beatmaps.setPageTitle
import io.beatmaps.upload.simple
import kotlinx.html.INPUT
import kotlinx.html.InputType
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.buildElement
import react.createRef
import react.dom.*
import react.router.dom.RouteResultHistory
import react.setState

external interface DropInfo {
    val getRootProps: () -> dynamic
    val getInputProps: () -> dynamic
}
fun <P> RBuilder.renderChild(rc: RBuilder.(P) -> Unit) {
    childList += { p: P -> buildElement { rc(p) } }
}

class UploadRequestConfig(block: (AxiosProgress) -> Unit) : AxiosRequestConfig {
    override var onUploadProgress: ((progressEvent: AxiosProgress) -> Unit)? = block
    override var validateStatus: ((Number) -> Boolean)? = {
        arrayOf(200, 400).contains(it)
    }
}

external interface UploadPageProps : RProps {
    var history: RouteResultHistory
}

data class UploadPageState(var errors: List<String> = listOf(), var loading: Boolean = false) : RState

@JsExport
class UploadPage : RComponent<UploadPageProps, UploadPageState>() {
    private val captchaRef = createRef<ReCAPTCHA>()
    private val titleRef = createRef<HTMLInputElement>()
    private val descrRef = createRef<HTMLInputElement>()
    private val progressBarInnerRef = createRef<HTMLElement>()

    init {
        state = UploadPageState()
    }

    override fun componentDidMount() {
        setPageTitle("Upload")
    }

    override fun RBuilder.render() {
        div("row") {
            div("col-7 m-auto") {
                h2 {
                    +"Upload Map"
                }
                form("") {
                    fieldSet {
                        div("form-group") {
                            label {
                                attrs.htmlFor = "name"
                                +"Title"
                            }
                            input(InputType.text, classes ="form-control") {
                                attrs.disabled = state.loading
                                ref = titleRef
                            }
                        }

                        div("form-group") {
                            label {
                                attrs.htmlFor = "description"
                                +"Description"
                            }
                            textArea("10", classes ="form-control") {
                                attrs.disabled = state.loading
                                ref = descrRef
                            }
                        }

                        Dropzone.default {
                            simple(props.history, state.loading, state.errors.isNotEmpty(), progressBarInnerRef,
                                "Drag and drop some files here, or click to select files", captchaRef, {
                                setState {
                                    loading = true
                                }
                                val titleInput = titleRef.current
                                val descrInput = descrRef.current

                                it.append("title", titleInput?.value ?: "")
                                it.append("description", descrInput?.value ?: "")
                            }, {
                                setState {
                                    errors = it
                                    loading = false
                                }
                            })
                        }

                        if (!state.loading) {
                            state.errors.forEach {
                                div("invalid-feedback") {
                                    +it
                                }
                            }
                        }

                        ReCAPTCHA.default {
                            attrs.sitekey = "6LdMpxUaAAAAAA6a3Fb2BOLQk9KO8wCSZ-a_YIaH"
                            attrs.size = "invisible"
                            ref = captchaRef
                        }

                        /*child(ReCAPTCHA.default::class) {
                            attrs.sitekey = "6LdMpxUaAAAAAA6a3Fb2BOLQk9KO8wCSZ-a_YIaH"
                            attrs.size = "invisible"
                            ref = captchaRef
                        }*/
                    }
                }
            }
        }
    }
}