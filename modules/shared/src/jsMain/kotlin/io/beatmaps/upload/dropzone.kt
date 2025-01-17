package io.beatmaps.upload

import external.Axios
import external.AxiosResponse
import external.DropzoneProps
import io.beatmaps.History
import io.beatmaps.api.FailedUploadResponse
import io.beatmaps.api.UploadValidationInfo
import io.beatmaps.captcha.ICaptchaHandler
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromDynamic
import org.w3c.dom.HTMLElement
import org.w3c.xhr.FormData
import react.Props
import react.RElementBuilder
import react.RefObject
import react.createElement
import react.dom.aria.AriaRole
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.small
import web.cssom.ClassName

fun RElementBuilder<DropzoneProps>.simple(
    history: History,
    loading: Boolean,
    progressBarInnerRef: RefObject<HTMLElement>,
    dropText: String,
    captchaRef: RefObject<ICaptchaHandler>,
    block: (FormData) -> Unit,
    errorsBlock: (List<UploadValidationInfo>) -> Unit,
    extraInfo: List<String> = emptyList(),
    successBlock: ((AxiosResponse<dynamic>) -> Unit)? = null
) {
    attrs.onDrop = { file ->
        captchaRef.current?.execute()?.then({
            val data = FormData().also(block)
            data.append("recaptcha", it)
            data.asDynamic().append("file", file[0]) // Kotlin doesn't have an equivalent method to this js

            Axios.post<dynamic>(
                "/upload", data,
                UploadRequestConfig { progress ->
                    val v = ((progress.loaded * 100f) / progress.total).toInt()
                    progressBarInnerRef.current?.style?.width = "$v%"
                }
            ).then({ r ->
                if (r.status == 200) {
                    if (successBlock != null) {
                        successBlock(r)
                    } else {
                        history.push("/maps/${r.data}")
                    }
                } else if (r.status == 401) {
                    errorsBlock(listOf(UploadValidationInfo("Not logged in")))
                } else if (r.status == 413) {
                    errorsBlock(listOf(UploadValidationInfo("Zip file too big")))
                } else {
                    captchaRef.current?.reset()
                    val failedResponse = Json.decodeFromDynamic<FailedUploadResponse>(r.data)
                    errorsBlock(failedResponse.errors)
                }
            }) {
                errorsBlock(listOf(UploadValidationInfo("Internal server error")))
            }
        }) {
            errorsBlock(listOf(UploadValidationInfo(it.message ?: "Unknown error")))
        }
    }
    attrs.children = { info ->
        createElement<Props> {
            div {
                attrs.className = ClassName("dropzone")
                val rootProps = info.getRootProps()
                val props = info.getInputProps()
                attrs {
                    onKeyDown = rootProps.onKeyDown
                    onFocus = rootProps.onFocus
                    onBlur = rootProps.onBlur
                    onClick = rootProps.onClick
                    onDragEnter = rootProps.onDragEnter
                    onDragOver = rootProps.onDragOver
                    onDragLeave = rootProps.onDragLeave
                    onDrop = rootProps.onDrop
                    tabIndex = rootProps.tabIndex ?: 0
                }
                ref = rootProps.ref

                input {
                    attrs {
                        type = props.type
                        className = ClassName("d-none")
                        accept = props.accept ?: ""
                        multiple = props.multiple
                        onChange = props.onChange
                        onClick = props.onClick
                        autoComplete = props.autoComplete
                        tabIndex = props.tabIndex
                    }
                    ref = props.ref
                }

                div {
                    attrs.className = ClassName("progress")
                    attrs.hidden = !loading
                    div {
                        attrs.className = ClassName("progress-bar progress-bar-striped progress-bar-animated bg-info")
                        attrs.role = AriaRole.progressbar
                        ref = progressBarInnerRef
                    }
                }

                div {
                    attrs.hidden = loading
                    i {
                        attrs.className = ClassName("fas fa-upload")
                    }
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
