package io.beatmaps.upload

import external.AxiosProgress
import external.AxiosRequestConfig
import external.Dropzone
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
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import react.Props
import react.dom.events.SyntheticEvent
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.fieldset
import react.dom.html.ReactHTML.form
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.h4
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.strong
import react.dom.html.ReactHTML.textarea
import react.dom.html.ReactHTML.ul
import react.router.useNavigate
import react.useCallback
import react.useEffectOnce
import react.useMemo
import react.useRef
import react.useState
import web.autofill.AutoFillBase
import web.cssom.ClassName
import web.html.InputType
import web.window.WindowTarget

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

    div {
        attrs.className = ClassName("row")
        div {
            attrs.className = ClassName("col-7")
            h2 {
                +"Upload Map"
            }
            form {
                fieldset {
                    div {
                        attrs.className = ClassName("mb-3")
                        label {
                            attrs.className = ClassName("form-label")
                            attrs.htmlFor = "name"
                            +"Title"
                        }
                        input {
                            attrs.type = InputType.text
                            attrs.className = ClassName("form-control" + (if (hasTitle == false) " is-invalid" else ""))
                            attrs.id = "name"
                            attrs.disabled = loading
                            ref = titleRef
                            val checkForValue = { _: SyntheticEvent<*, *> ->
                                updateHasTitle()
                            }
                            attrs.onChange = checkForValue
                            attrs.onBlur = checkForValue
                        }
                        div {
                            attrs.className = ClassName("invalid-feedback")
                            +"Enter a title"
                        }
                    }

                    div {
                        attrs.className = ClassName("mb-3")
                        label {
                            attrs.className = ClassName("form-label")
                            attrs.htmlFor = "description"
                            +"Description"
                        }
                        textarea {
                            attrs.rows = 10
                            attrs.className = ClassName("form-control")
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
                                label {
                                    attrs.className = ClassName("form-label")
                                    val allocationInfo = MapTag.maxPerType.map { "${byType.getValue(it.key)}/${it.value} ${it.key.name}" }.joinToString(", ")
                                    +"Tags ($allocationInfo):"
                                }
                            }
                        }
                    }

                    div {
                        attrs.className = ClassName("mb-3")
                        input {
                            attrs.type = InputType.radio
                            attrs.name = "beatsage"
                            attrs.className = ClassName("btn-check")
                            attrs.id = "beatsage-no"
                            attrs.autoComplete = AutoFillBase.off
                            attrs.checked = false
                            attrs.onChange = {
                                setBeatsage(false)
                                updateHasTitle()
                            }
                        }
                        label {
                            attrs.className = ClassName("btn btn-outline-light")
                            attrs.htmlFor = "beatsage-no"
                            +"I made this map myself with no"
                            br {}
                            +"AI assistance"
                        }

                        input {
                            attrs.type = InputType.radio
                            attrs.name = "beatsage"
                            attrs.className = ClassName("btn-check")
                            attrs.id = "beatsage-yes"
                            ref = beatsageRef
                            attrs.autoComplete = AutoFillBase.off
                            attrs.checked = false
                            attrs.onChange = {
                                setBeatsage(true)
                                updateHasTitle()
                            }
                        }
                        label {
                            attrs.className = ClassName("btn btn-outline-light")
                            attrs.htmlFor = "beatsage-yes"
                            +"BeatSage or another AI mapping tool was used to create this map"
                        }
                    }

                    if (hasTitle == true && beatsage != null) {
                        Dropzone.default {
                            simple(
                                history, loading, progressBarInnerRef,
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
        div {
            attrs.className = ClassName("col-5")
            div {
                attrs.className = ClassName("card bg-danger mb-3")
                div {
                    attrs.className = ClassName("card-body")
                    h4 {
                        attrs.className = ClassName("card-title")
                        +"Map Testing"
                    }
                    p {
                        attrs.className = ClassName("card-text")
                        +"You do "
                        strong {
                            +"NOT"
                        }
                        +" need to upload your map to test it in game."
                    }
                    p {
                        attrs.className = ClassName("card-text")
                        +"On PC you can access WIPs directly if you have SongCore."
                        br {}
                        +"On Quest you can follow "
                        a {
                            attrs.href = "https://bsmg.wiki/mapping/#testing-on-a-quest"
                            attrs.target = WindowTarget._blank
                            attrs.rel = "noopener"
                            +"the guide on the BSMG wiki"
                        }
                        +"."
                        br {}
                        +"If you need help head over to the "
                        a {
                            attrs.href = "https://discord.com/channels/441805394323439646/443569023951568906"
                            attrs.target = WindowTarget._blank
                            attrs.rel = "noopener"
                            +"BSMG discord"
                        }
                        +"."
                    }
                    p {
                        attrs.className = ClassName("card-text")
                        +"WIP maps will be removed."
                    }
                }
            }
            div {
                attrs.className = ClassName("card bg-blue mb-3")
                div {
                    attrs.className = ClassName("card-body")
                    h4 {
                        attrs.className = ClassName("card-title")
                        +"AI Mapping"
                    }
                    p {
                        attrs.className = ClassName("card-text")
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
            div {
                attrs.className = ClassName("card bg-secondary mb-3")
                div {
                    attrs.className = ClassName("card-body")
                    p {
                        attrs.className = ClassName("card-text")
                        +"By uploading your map, you acknowledge that you agree to our "
                        a {
                            attrs.href = "/policy/tos"
                            +"Terms of Service"
                        }
                        +"."
                    }
                }
            }
        }
    }

    div {
        attrs.className = ClassName("row")
        if (!loading) {
            errors {
                attrs.validationErrors = errors
            }
        }
    }
}
