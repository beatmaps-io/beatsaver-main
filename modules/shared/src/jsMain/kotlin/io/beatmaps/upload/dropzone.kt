package io.beatmaps.upload

import external.Axios
import external.AxiosResponse
import external.DropInfo
import external.DropzoneProps
import io.beatmaps.History
import io.beatmaps.api.FailedUploadResponse
import io.beatmaps.api.UploadValidationInfo
import io.beatmaps.captcha.ICaptchaHandler
import io.beatmaps.util.fcmemo
import js.objects.jso
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromDynamic
import react.Props
import react.Ref
import react.RefObject
import react.createElement
import react.dom.aria.AriaRole
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.small
import web.cssom.ClassName
import web.form.FormData
import web.html.HTMLElement

fun DropzoneProps.simple(
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
    onDrop = { file ->
        captchaRef.current?.execute()?.then({
            val data = FormData().also(block)
            data.append("recaptcha", it)
            data.append("file", file[0])

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
    children = { info ->
        createElement(
            dropzoneDiv,
            jso {
                dropInfo = info
                this.loading = loading
                this.progressBarInnerRef = progressBarInnerRef
                this.dropText = dropText
                this.extraInfo = extraInfo
            }
        )
    }
}

external interface DropzoneDivProps : Props {
    var dropInfo: DropInfo
    var loading: Boolean
    var progressBarInnerRef: Ref<HTMLElement>
    var dropText: String
    var extraInfo: List<String>
}

val dropzoneDiv = fcmemo<DropzoneDivProps>("DropzoneDiv") { props ->
    div {
        className = ClassName("dropzone")
        val rootProps = props.dropInfo.getRootProps()
        val inputProps = props.dropInfo.getInputProps()

        onKeyDown = rootProps.onKeyDown
        onFocus = rootProps.onFocus
        onBlur = rootProps.onBlur
        onClick = rootProps.onClick
        onDragEnter = rootProps.onDragEnter
        onDragOver = rootProps.onDragOver
        onDragLeave = rootProps.onDragLeave
        onDrop = rootProps.onDrop
        tabIndex = rootProps.tabIndex ?: 0
        ref = rootProps.ref

        input {
            type = inputProps.type
            className = ClassName("d-none")
            accept = inputProps.accept ?: ""
            multiple = inputProps.multiple
            onChange = inputProps.onChange
            onClick = inputProps.onClick
            autoComplete = inputProps.autoComplete
            tabIndex = inputProps.tabIndex
            ref = inputProps.ref
        }

        div {
            className = ClassName("progress")
            hidden = !props.loading
            div {
                className = ClassName("progress-bar progress-bar-striped progress-bar-animated bg-info")
                role = AriaRole.progressbar
                ref = props.progressBarInnerRef
            }
        }

        div {
            hidden = props.loading
            i {
                className = ClassName("fas fa-upload")
            }
            p {
                +props.dropText
            }
            small {
                +"Max file size: 15MiB"
            }
            props.extraInfo.forEach {
                small {
                    +it
                }
            }
        }
    }
}
