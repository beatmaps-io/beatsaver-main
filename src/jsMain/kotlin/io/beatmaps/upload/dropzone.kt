package io.beatmaps.upload

import Axios
import AxiosResponse
import external.DropzoneProps
import external.ReCAPTCHA
import io.beatmaps.api.FailedUploadResponse
import io.beatmaps.maps.DropInfo
import io.beatmaps.maps.UploadRequestConfig
import io.beatmaps.maps.renderChild
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
import org.w3c.dom.events.Event
import org.w3c.xhr.FormData
import react.RElementBuilder
import react.RReadableRef
import react.dom.*
import react.router.dom.RouteResultHistory

fun RElementBuilder<DropzoneProps>.simple(history: RouteResultHistory, loading: Boolean, errors: Boolean, progressBarInnerRef: RReadableRef<HTMLElement>, dropText: String,
                                          captchaRef: RReadableRef<ReCAPTCHA>, block: (FormData) -> Unit, errorsBlock: (List<String>) -> Unit, successBlock: ((AxiosResponse<dynamic>) -> Unit)? = null) {
    attrs.onDrop = { file ->
        captchaRef.current?.executeAsync()?.then {
            val data = FormData().also(block)
            data.asDynamic().append("file", file[0]) // Kotlin doesn't have an equivalent method to this js
            data.append("recaptcha", it)

            Axios.post<dynamic>("/upload", data, UploadRequestConfig { progress ->
                val v = ((progress.loaded * 100f) / progress.total).toInt()
                progressBarInnerRef.current?.style?.width = "$v%"
            }).then { r ->
                if (r.status == 200) {
                    if (successBlock != null) {
                        successBlock(r)
                    } else {
                        history.push("/maps/" + (r.data as String))
                    }
                } else {
                    val failedResponse = Json.decodeFromDynamic<FailedUploadResponse>(r.data)
                    errorsBlock(failedResponse.errors)
                }
            }.catch {
                errorsBlock(listOf("Internal server error"))
            }
        }
    }
    renderChild { info: DropInfo ->
        div("dropzone" + (if (errors) " is-invalid" else "")) {
            val rootProps = info.getRootProps()
            val props = info.getInputProps()
            attrs {
                onKeyDownFunction = rootProps.onKeyDown as (Event) -> Unit
                onFocusFunction = rootProps.onFocus as (Event) -> Unit
                onBlurFunction = rootProps.onBlur as (Event) -> Unit
                onClickFunction = rootProps.onClick as (Event) -> Unit
                onDragEnterFunction = rootProps.onDragEnter as (Event) -> Unit
                onDragOverFunction = rootProps.onDragOver as (Event) -> Unit
                onDragLeaveFunction = rootProps.onDragLeave as (Event) -> Unit
                onDropFunction = rootProps.onDrop as (Event) -> Unit
                tabIndex = (rootProps.tabIndex as Int? ?: 0).toString()
            }
            ref {
                rootProps.ref.current = findDOMNode(it)
            }

            input(InputType.valueOf(props.type as String), classes = "d-none") {
                attrs {
                    accept = props.accept ?: ""
                    multiple = props.multiple as Boolean
                    onChangeFunction = props.onChange as (Event) -> Unit
                    onClickFunction = props.onClick as (Event) -> Unit
                    autoComplete = (props.autoComplete as String) == "on"
                    tabIndex = (props.tabIndex as Int).toString()
                }

                ref {
                    props.ref.current = findDOMNode(it)
                }
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
                    +"Max file size: 15MB"
                }
            }
        }
    }
}