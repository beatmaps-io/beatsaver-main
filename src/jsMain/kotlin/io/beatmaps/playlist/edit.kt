package io.beatmaps.playlist

import external.Axios
import external.ReCAPTCHA
import external.generateConfig
import external.recaptcha
import io.beatmaps.api.FailedUploadResponse
import io.beatmaps.api.PlaylistFull
import io.beatmaps.api.PlaylistPage
import io.beatmaps.common.Config
import io.beatmaps.setPageTitle
import io.beatmaps.upload.UploadRequestConfig
import kotlinx.browser.window
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.hidden
import kotlinx.html.id
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onSubmitFunction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromDynamic
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.files.get
import org.w3c.xhr.FormData
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
import react.createRef
import react.dom.button
import react.dom.div
import react.dom.form
import react.dom.input
import react.dom.jsStyle
import react.dom.label
import react.dom.textarea
import react.router.dom.RouteResultHistory
import react.router.dom.routeLink
import react.setState

external interface PlaylistEditProps : RProps {
    var id: Int?
    var history: RouteResultHistory
}

external interface PlaylistEditState : RState {
    var playlist: PlaylistFull?
    var loading: Boolean?
    var filename: String?
    var success: Boolean?
    var errors: List<String>?
}

class EditPlaylist : RComponent<PlaylistEditProps, PlaylistEditState>() {
    private val captchaRef = createRef<ReCAPTCHA>()
    private val coverRef = createRef<HTMLInputElement>()

    private val nameRef = createRef<HTMLInputElement>()
    private val descriptionRef = createRef<HTMLTextAreaElement>()
    private val publicRef = createRef<HTMLInputElement>()

    override fun componentDidMount() {
        setPageTitle("Playlist")

        if (props.id == null) {
            setState {
                loading = false
                playlist = null
            }
            window.setTimeout(
                {
                    publicRef.current?.checked = true
                },
                1
            )
        } else {
            loadData()
        }
    }

    private fun loadData() {
        if (state.loading == true)
            return

        setState {
            loading = true
        }

        Axios.get<PlaylistPage>(
            "${Config.apibase}/playlists/id/${props.id}",
            generateConfig<String, PlaylistPage>()
        ).then {
            setPageTitle("Playlist - ${it.data.playlist?.name}")

            window.setTimeout(
                {
                    nameRef.current?.value = it.data.playlist?.name ?: ""
                    descriptionRef.current?.value = it.data.playlist?.description ?: ""
                    publicRef.current?.checked = it.data.playlist?.public ?: false
                },
                1
            )

            setState {
                loading = false
                playlist = it.data.playlist
            }
        }.catch {
            props.history.push("/")
        }
    }

    override fun RBuilder.render() {
        if (state.loading == false) {
            div("card border-dark") {
                div("card-header") {
                    +((if (props.id == null) "Create" else "Edit") + " playlist")
                }
                form(classes = "card-body") {
                    attrs.onSubmitFunction = { ev ->
                        ev.preventDefault()

                        setState {
                            loading = true
                        }

                        fun sendForm(data: FormData) {
                            data.append("name", nameRef.current?.value ?: "")
                            data.append("description", descriptionRef.current?.value ?: "")
                            data.append("public", (publicRef.current?.checked ?: false).toString())
                            val file = coverRef.current?.files?.let { it[0] }
                            if (file != null) {
                                data.asDynamic().append("file", file) // Kotlin doesn't have an equivalent method to this js
                            }

                            Axios.post<dynamic>(
                                Config.apibase + "/playlists" + if (props.id == null) "/create" else "/id/${props.id}/edit", data,
                                UploadRequestConfig { }
                            ).then { r ->
                                if (r.status == 200) {
                                    props.history.push("/playlists/${props.id ?: r.data}")
                                } else {
                                    captchaRef.current?.reset()
                                    val failedResponse = Json.decodeFromDynamic<FailedUploadResponse>(r.data)
                                    setState {
                                        errors = failedResponse.errors
                                        loading = false
                                        success = failedResponse.success
                                    }
                                }
                            }.catch {
                                setState {
                                    errors = listOf("Internal server error")
                                    loading = false
                                    success = false
                                }
                            }
                        }

                        val data = FormData()
                        captchaRef.current?.executeAsync()?.then {
                            data.append("recaptcha", it)
                            sendForm(data)
                        } ?: run {
                            sendForm(data)
                        }
                    }
                    div("mb-3") {
                        label("form-label") {
                            attrs.htmlFor = "name"
                            +"Name"
                        }
                        input(type = InputType.text, classes = "form-control") {
                            key = "name"
                            ref = nameRef
                            attrs.id = "name"
                            attrs.placeholder = "Name"
                            attrs.disabled = state.loading == true
                            attrs.required = true
                            attrs.autoFocus = true
                        }
                    }
                    div("mb-3") {
                        label("form-label") {
                            attrs.htmlFor = "description"
                            +"Description"
                        }
                        textarea("10", classes = "form-control") {
                            attrs.id = "description"
                            attrs.disabled = state.loading == true
                            ref = descriptionRef
                        }
                    }
                    div("form-check form-switch mb-3") {
                        input(InputType.checkBox, classes = "form-check-input") {
                            attrs.id = "public"
                            attrs.disabled = state.loading == true
                            ref = publicRef
                        }
                        label("form-check-label") {
                            attrs.htmlFor = "public"
                            +"Public"
                        }
                    }
                    div("mb-3 w-25") {
                        label("form-label") {
                            attrs.htmlFor = "cover"
                            div("text-truncate") {
                                +"Cover image"
                            }
                        }
                        input(InputType.file, classes = "form-control") {
                            attrs.onChangeFunction = {
                                val file = coverRef.current?.files?.let { it[0] }
                                setState {
                                    filename = file?.name
                                }
                            }
                            key = "cover"
                            attrs.id = "cover"
                            ref = coverRef
                            attrs.hidden = state.loading == true
                        }
                    }
                    state.errors?.forEach {
                        div("invalid-feedback") {
                            attrs.jsStyle {
                                display = "block"
                            }
                            +it
                        }
                    }
                    div("btn-group w-100 mt-5") {
                        routeLink(props.id?.let { "/playlists/$it" } ?: "/", className = "btn btn-secondary") {
                            +"Cancel"
                        }
                        if (props.id == null) {
                            // Middle element otherwise the button corners don't round properly
                            recaptcha(captchaRef)
                        }
                        button(classes = "btn btn-success", type = ButtonType.submit) {
                            attrs.disabled = state.loading == true
                            +(if (props.id == null) "Create" else "Save")
                        }
                    }
                }
            }
        }
    }
}

fun RBuilder.editPlaylist(handler: PlaylistEditProps.() -> Unit): ReactElement {
    return child(EditPlaylist::class) {
        this.attrs(handler)
    }
}
