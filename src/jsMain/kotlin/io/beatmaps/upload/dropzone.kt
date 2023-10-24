package io.beatmaps.upload

import external.Axios
import external.AxiosResponse
import external.DropzoneProps
import external.ReCAPTCHA
import io.beatmaps.History
import io.beatmaps.api.FailedUploadResponse
import io.beatmaps.api.UploadValidationInfo
import kotlinx.html.InputType
import kotlinx.html.hidden
import kotlinx.html.js.onBlurFunction
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import kotlinx.html.js.onDragEnterFunction
import kotlinx.html.js.onDragLeaveFunction
import kotlinx.html.js.onDragOverFunction
import kotlinx.html.js.onDropFunction
import kotlinx.html.js.onFocusFunction
import kotlinx.html.js.onKeyDownFunction
import kotlinx.html.role
import kotlinx.html.tabIndex
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromDynamic
import org.w3c.dom.HTMLElement
import org.w3c.xhr.FormData
import react.Props
import react.RElementBuilder
import react.RefObject
import react.createElement
import react.dom.attrs
import react.dom.div
import react.dom.i
import react.dom.input
import react.dom.p
import react.dom.small

fun RElementBuilder<DropzoneProps>.simple(
    history: History,
    loading: Boolean,
    errors: Boolean,
    progressBarInnerRef: RefObject<HTMLElement>,
    dropText: String,
    captchaRef: RefObject<ReCAPTCHA>,
    block: (FormData) -> Unit,
    errorsBlock: (List<UploadValidationInfo>) -> Unit,
    extraInfo: List<String> = emptyList(),
    successBlock: ((AxiosResponse<dynamic>) -> Unit)? = null
) {
    attrs.onDrop = { file ->
        captchaRef.current?.executeAsync()?.then {
            val data = FormData().also(block)
            data.append("recaptcha", it)
            data.asDynamic().append("file", file[0]) // Kotlin doesn't have an equivalent method to this js

            Axios.post<dynamic>(
                "/upload", data,
                UploadRequestConfig { progress ->
                    val v = ((progress.loaded * 100f) / progress.total).toInt()
                    progressBarInnerRef.current?.style?.width = "$v%"
                }
            ).then { r ->
                if (r.status == 200) {
                    if (successBlock != null) {
                        successBlock(r)
                    } else {
                        history.push("/maps/${r.data}")
                    }
                } else if (r.status == 413) {
                    errorsBlock(listOf(UploadValidationInfo(listOf(), "Zip file too big")))
                } else {
                    captchaRef.current?.reset()
                    val failedResponse = Json.decodeFromDynamic<FailedUploadResponse>(r.data)
                    errorsBlock(failedResponse.errors)
                }
            }.catch {
                errorsBlock(listOf(UploadValidationInfo(listOf(), "Internal server error")))
            }
        }
    }
    attrs.children = { info ->
        createElement<Props> {
            div("dropzone" + (if (errors) " is-invalid" else "")) {
                val rootProps = info.getRootProps()
                val props = info.getInputProps()
                attrs {
                    onKeyDownFunction = rootProps.onKeyDown
                    onFocusFunction = rootProps.onFocus
                    onBlurFunction = rootProps.onBlur
                    onClickFunction = rootProps.onClick
                    onDragEnterFunction = rootProps.onDragEnter
                    onDragOverFunction = rootProps.onDragOver
                    onDragLeaveFunction = rootProps.onDragLeave
                    onDropFunction = rootProps.onDrop
                    tabIndex = (rootProps.tabIndex ?: 0).toString()
                }
                ref = rootProps.ref

                input(InputType.valueOf(props.type), classes = "d-none") {
                    attrs {
                        accept = props.accept ?: ""
                        multiple = props.multiple
                        onChangeFunction = props.onChange
                        onClickFunction = props.onClick
                        autoComplete = props.autoComplete == "on"
                        tabIndex = props.tabIndex.toString()
                    }
                    ref = props.ref
                }

                div("progress") {
                    attrs.hidden = !loading
                    div("progress-bar progress-bar-striped progress-bar-animated bg-info") {
                        attrs.role = "progressbar"
                        ref = progressBarInnerRef
                    }
                }

                div {
                    attrs.hidden = loading
                    i("fas fa-upload") {}
                    p {
                        +dropText
                    }
                    small {
                        +"Max file size: 15MiB"
                    }
                    extraInfo.forEach {
                        small {
                            +it
                        }
                    }
                }
            }
        }
    }
}
