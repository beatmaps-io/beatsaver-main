package io.beatmaps.upload

import external.AxiosProgress
import external.AxiosRequestConfig
import external.Dropzone
import external.reactFor
import io.beatmaps.History
import io.beatmaps.api.UploadValidationInfo
import io.beatmaps.captcha.ICaptchaHandler
import io.beatmaps.captcha.captcha
import io.beatmaps.common.MapTag
import io.beatmaps.maps.TagPickerHeadingRenderer
import io.beatmaps.maps.tagPicker
import io.beatmaps.setPageTitle
import io.beatmaps.shared.form.errors
import io.beatmaps.util.fcmemo
import kotlinx.html.InputType
import kotlinx.html.id
import kotlinx.html.js.onBlurFunction
import kotlinx.html.js.onChangeFunction
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import react.Props
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
import react.router.useNavigate
import react.useCallback
import react.useEffectOnce
import react.useMemo
import react.useRef
import react.useState

class UploadRequestConfig(block: (AxiosProgress) -> Unit) : AxiosRequestConfig {
    override var onUploadProgress: ((progressEvent: AxiosProgress) -> Unit)? = block
    override var validateStatus: ((Number) -> Boolean)? = {
        arrayOf(200, 400, 401, 413).contains(it)
    }
}

val uploadPage = fcmemo<Props>("uploadPage") {
    val (errors, setErrors) = useState(listOf<UploadValidationInfo>())
    val (loading, setLoading) = useState(false)
    val (beatsage, setBeatsage) = useState<Boolean>()
    val (hasTitle, setHasTitle) = useState<Boolean>()
    val (tags, setTags) = useState(setOf<MapTag>())

    val captchaRef = useRef<ICaptchaHandler>()
    val titleRef = useRef<HTMLInputElement>()
    val descrRef = useRef<HTMLInputElement>()
    val beatsageRef = useRef<HTMLInputElement>()
    val progressBarInnerRef = useRef<HTMLElement>()

    val history = History(useNavigate())

    useEffectOnce {
        setPageTitle("Upload")
    }

    fun updateHasTitle() {
        val newValue = titleRef.current?.value?.isNotEmpty() == true
        if (newValue != hasTitle) {
            setHasTitle(newValue)
        }
    }

    div("row") {
        div("col-7") {
            h2 {
                +"Upload Map"
            }
            form("") {
                fieldset {
                    div("mb-3") {
                        label("form-label") {
                            attrs.reactFor = "name"
                            +"Title"
                        }
                        input(InputType.text, classes = "form-control" + (if (hasTitle == false) " is-invalid" else "")) {
                            attrs.id = "name"
                            attrs.disabled = loading
                            ref = titleRef
                            val checkForValue = { _: Event ->
                                updateHasTitle()
                            }
                            attrs.onChangeFunction = checkForValue
                            attrs.onBlurFunction = checkForValue
                        }
                        div("invalid-feedback") {
                            +"Enter a title"
                        }
                    }

                    div("mb-3") {
                        label("form-label") {
                            attrs.reactFor = "description"
                            +"Description"
                        }
                        textarea("10", classes = "form-control") {
                            attrs.id = "description"
                            attrs.disabled = loading
                            ref = descrRef
                        }
                    }

                    tagPicker {
                        attrs.classes = "ul-tags mb-3"
                        attrs.tags = tags
                        attrs.tagUpdateCallback = useCallback { it: Set<MapTag> ->
                            setTags(it)
                        }
                        attrs.renderHeading = useMemo {
                            TagPickerHeadingRenderer { byType ->
                                label("form-label") {
                                    val allocationInfo = MapTag.maxPerType.map { "${byType.getValue(it.key)}/${it.value} ${it.key.name}" }.joinToString(", ")
                                    +"Tags ($allocationInfo):"
                                }
                            }
                        }
                    }

                    div("mb-3") {
                        input(InputType.radio, name = "beatsage", classes = "btn-check") {
                            attrs.id = "beatsage-no"
                            attrs.autoComplete = false
                            attrs.checked = false
                            attrs.onChangeFunction = {
                                setBeatsage(false)
                                updateHasTitle()
                            }
                        }
                        label("btn btn-outline-light") {
                            attrs.reactFor = "beatsage-no"
                            +"I made this map myself with no"
                            br {}
                            +"AI assistance"
                        }

                        input(InputType.radio, name = "beatsage", classes = "btn-check") {
                            attrs.id = "beatsage-yes"
                            ref = beatsageRef
                            attrs.autoComplete = false
                            attrs.checked = false
                            attrs.onChangeFunction = {
                                setBeatsage(true)
                                updateHasTitle()
                            }
                        }
                        label("btn btn-outline-light") {
                            attrs.reactFor = "beatsage-yes"
                            +"BeatSage or another AI mapping tool was used to create this map"
                        }
                    }

                    if (hasTitle == true && beatsage != null) {
                        Dropzone.default {
                            simple(
                                history, loading, errors.isNotEmpty(), progressBarInnerRef,
                                "Drag and drop some files here, or click to select files", captchaRef,
                                {
                                    setLoading(true)
                                    val titleInput = titleRef.current
                                    val descrInput = descrRef.current
                                    val beatsageInput = beatsageRef.current
                                    val tagsStr = tags.joinToString(",") { t -> t.slug }

                                    it.append("title", titleInput?.value ?: "")
                                    it.append("description", descrInput?.value ?: "")
                                    it.append("tags", tagsStr)
                                    it.append("beatsage", if (beatsageInput?.checked == true) "true" else "")
                                },
                                {
                                    setErrors(it)
                                    setLoading(false)
                                }
                            )
                        }
                    }

                    captcha {
                        key = "captcha"
                        attrs.captchaRef = captchaRef
                        attrs.page = "upload"
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
            div("card bg-blue mb-3") {
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
            div("card bg-secondary mb-3") {
                div("card-body") {
                    p("card-text") {
                        +"By uploading your map, you acknowledge that you agree to our "
                        a("/policy/tos") {
                            +"Terms of Service"
                        }
                        +"."
                    }
                }
            }
        }
    }

    div("row") {
        if (!loading) {
            errors {
                attrs.validationErrors = errors
            }
        }
    }
}
