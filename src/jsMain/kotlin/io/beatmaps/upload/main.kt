package io.beatmaps.maps

import external.AxiosProgress
import external.AxiosRequestConfig
import external.Dropzone
import external.ReCAPTCHA
import io.beatmaps.setPageTitle
import io.beatmaps.upload.simple
import kotlinx.html.InputType
import kotlinx.html.id
import kotlinx.html.js.onChangeFunction
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.buildElement
import react.createRef
import react.dom.a
import react.dom.br
import react.dom.div
import react.dom.fieldset
import react.dom.form
import react.dom.h2
import react.dom.h4
import react.dom.input
import react.dom.label
import react.dom.li
import react.dom.p
import react.dom.strong
import react.dom.textarea
import react.dom.ul
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

data class UploadPageState(var errors: List<String> = listOf(), var loading: Boolean = false, var beatsage: Boolean? = null) : RState

@JsExport
class UploadPage : RComponent<UploadPageProps, UploadPageState>() {
    private val captchaRef = createRef<ReCAPTCHA>()
    private val titleRef = createRef<HTMLInputElement>()
    private val descrRef = createRef<HTMLInputElement>()
    private val beatsageRef = createRef<HTMLInputElement>()
    private val progressBarInnerRef = createRef<HTMLElement>()

    init {
        state = UploadPageState()
    }

    override fun componentDidMount() {
        setPageTitle("Upload")
    }

    override fun RBuilder.render() {
        div("row") {
            div("col-7") {
                h2 {
                    +"Upload Map"
                }
                form("") {
                    fieldset {
                        div("form-group") {
                            label {
                                attrs.htmlFor = "name"
                                +"Title"
                            }
                            input(InputType.text, classes = "form-control") {
                                attrs.id = "name"
                                attrs.disabled = state.loading
                                ref = titleRef
                            }
                        }

                        div("form-group") {
                            label {
                                attrs.htmlFor = "description"
                                +"Description"
                            }
                            textarea("10", classes = "form-control") {
                                attrs.id = "description"
                                attrs.disabled = state.loading
                                ref = descrRef
                            }
                        }

                        div("form-group") {
                            input(InputType.radio, name = "beatsage", classes = "btn-check") {
                                attrs.id = "beatsage-no"
                                attrs.autoComplete = false
                                attrs.checked = false
                                attrs.onChangeFunction = {
                                    setState {
                                        beatsage = false
                                    }
                                }
                            }
                            label("btn btn-outline-light") {
                                attrs.htmlFor = "beatsage-no"
                                +"I made this map myself with no AI assistance"
                            }

                            input(InputType.radio, name = "beatsage", classes = "btn-check") {
                                attrs.id = "beatsage-yes"
                                ref = beatsageRef
                                attrs.autoComplete = false
                                attrs.checked = false
                                attrs.onChangeFunction = {
                                    setState {
                                        beatsage = true
                                    }
                                }
                            }
                            label("btn btn-outline-light") {
                                attrs.htmlFor = "beatsage-yes"
                                +"BeatSage or another AI mapping tool was used to create this map"
                            }
                        }

                        if (state.beatsage != null) {
                            Dropzone.default {
                                simple(
                                    props.history, state.loading, state.errors.isNotEmpty(), progressBarInnerRef,
                                    "Drag and drop some files here, or click to select files", captchaRef,
                                    {
                                        setState {
                                            loading = true
                                        }
                                        val titleInput = titleRef.current
                                        val descrInput = descrRef.current
                                        val beatsageInput = beatsageRef.current

                                        it.append("title", titleInput?.value ?: "")
                                        it.append("description", descrInput?.value ?: "")
                                        it.append("beatsage", if (beatsageInput?.checked == true) "true" else "")
                                    },
                                    {
                                        setState {
                                            errors = it
                                            loading = false
                                        }
                                    }
                                )
                            }
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
                    }
                }
            }
            div("col-5") {
                div("card bg-danger mb-3") {
                    div("card-body") {
                        h4("card-title") {
                            +"Map Testing"
                        }
                        p("card-text") {
                            +"You do "
                            strong {
                                +"NOT"
                            }
                            +" need to upload your map to test it in game."
                        }
                        p("card-text") {
                            +"On PC you can access WIPs directly if you have SongCore."
                            br {}
                            +"On Quest you can follow "
                            a("https://bsmg.wiki/mapping/#testing-on-a-quest", target = "_blank") {
                                attrs.rel = "noopener"
                                +"the guide on the BSMG wiki"
                            }
                            +"."
                            br {}
                            +"If you need help head over to the "
                            a("https://discord.com/channels/441805394323439646/443569023951568906", target = "_blank") {
                                attrs.rel = "noopener"
                                +"BSMG discord"
                            }
                            +"."
                        }
                        p("card-text") {
                            +"WIP maps will be removed."
                        }
                    }
                }
                div("card bg-info mb-3") {
                    div("card-body") {
                        h4("card-title") {
                            +"AI Mapping"
                        }
                        p("card-text") {
                            +"Auto-generated (AI) maps are allowed following these guidelines:"
                        }
                        ul {
                            li {
                                +"Identify the map as auto-generated on upload."
                            }
                            li {
                                +"Set the correct level author in your zip file."
                            }
                            li {
                                +"AI maps will not sync to the BeastSaber site."
                            }
                            li {
                                +"Personal maps do not need to be uploaded to play."
                                br {}
                                +"See Map Testing section."
                            }
                        }
                    }
                }
            }
        }
    }
}
