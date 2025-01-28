package io.beatmaps.upload

import external.AxiosProgress
import external.AxiosRequestConfig
import external.Dropzone
import io.beatmaps.History
import io.beatmaps.api.UploadResponse
import io.beatmaps.api.UploadValidationInfo
import io.beatmaps.captcha.ICaptchaHandler
import io.beatmaps.captcha.captcha
import io.beatmaps.common.MapTag
import io.beatmaps.common.json
import io.beatmaps.maps.TagPickerHeadingRenderer
import io.beatmaps.maps.tagPicker
import io.beatmaps.setPageTitle
import io.beatmaps.shared.form.errors
import io.beatmaps.util.fcmemo
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
import web.html.HTMLElement
import web.html.HTMLInputElement
import web.html.HTMLTextAreaElement
import web.html.InputType
import web.window.WindowTarget

class UploadRequestConfig(block: (AxiosProgress) -> Unit) : AxiosRequestConfig {
    override var onUploadProgress: ((progressEvent: AxiosProgress) -> Unit)? = block
    override var validateStatus: ((Number) -> Boolean)? = {
        arrayOf(200, 400, 401, 413).contains(it)
    }
    override var transformResponse: (String) -> UploadResponse = {
        json.decodeFromString(it)
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
    val descrRef = useRef<HTMLTextAreaElement>()
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
        className = ClassName("row")
        div {
            className = ClassName("col-7")
            h2 {
                +"Upload Map"
            }
            form {
                fieldset {
                    className = ClassName("position-relative")
                    div {
                        className = ClassName("mb-3")
                        label {
                            className = ClassName("form-label")
                            htmlFor = "name"
                            +"Title"
                        }
                        input {
                            type = InputType.text
                            className = ClassName("form-control" + (if (hasTitle == false) " is-invalid" else ""))
                            id = "name"
                            disabled = loading
                            ref = titleRef
                            val checkForValue = { _: SyntheticEvent<*, *> ->
                                updateHasTitle()
                            }
                            onChange = checkForValue
                            onBlur = checkForValue
                        }
                        div {
                            className = ClassName("invalid-feedback")
                            +"Enter a title"
                        }
                    }

                    div {
                        className = ClassName("mb-3")
                        label {
                            className = ClassName("form-label")
                            htmlFor = "description"
                            +"Description"
                        }
                        textarea {
                            rows = 10
                            className = ClassName("form-control")
                            id = "description"
                            disabled = loading
                            ref = descrRef
                        }
                    }

                    tagPicker {
                        classes = "ul-tags mb-3"
                        this.tags = tags
                        tagUpdateCallback = useCallback { it: Set<MapTag> ->
                            setTags(it)
                        }
                        renderHeading = useMemo {
                            TagPickerHeadingRenderer { byType ->
                                label {
                                    className = ClassName("form-label")
                                    val allocationInfo = MapTag.maxPerType.map { "${byType.getValue(it.key)}/${it.value} ${it.key.name}" }.joinToString(", ")
                                    +"Tags ($allocationInfo):"
                                }
                            }
                        }
                    }

                    div {
                        className = ClassName("mb-3")
                        input {
                            type = InputType.radio
                            name = "beatsage"
                            className = ClassName("btn-check")
                            id = "beatsage-no"
                            autoComplete = AutoFillBase.off
                            defaultChecked = false
                            onChange = {
                                setBeatsage(false)
                                updateHasTitle()
                            }
                        }
                        label {
                            className = ClassName("btn btn-outline-light")
                            htmlFor = "beatsage-no"
                            +"I made this map myself with no"
                            br {}
                            +"AI assistance"
                        }

                        input {
                            type = InputType.radio
                            name = "beatsage"
                            className = ClassName("btn-check")
                            id = "beatsage-yes"
                            ref = beatsageRef
                            autoComplete = AutoFillBase.off
                            defaultChecked = false
                            onChange = {
                                setBeatsage(true)
                                updateHasTitle()
                            }
                        }
                        label {
                            className = ClassName("btn btn-outline-light")
                            htmlFor = "beatsage-yes"
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
                        this.captchaRef = captchaRef
                        page = "upload"
                    }
                }
            }
        }
        div {
            className = ClassName("col-5")
            div {
                className = ClassName("card bg-danger mb-3")
                div {
                    className = ClassName("card-body")
                    h4 {
                        className = ClassName("card-title")
                        +"Map Testing"
                    }
                    p {
                        className = ClassName("card-text")
                        +"You do "
                        strong {
                            +"NOT"
                        }
                        +" need to upload your map to test it in game."
                    }
                    p {
                        className = ClassName("card-text")
                        +"On PC you can access WIPs directly if you have SongCore."
                        br {}
                        +"On Quest you can follow "
                        a {
                            href = "https://bsmg.wiki/mapping/#testing-on-a-quest"
                            target = WindowTarget._blank
                            rel = "noopener"
                            +"the guide on the BSMG wiki"
                        }
                        +"."
                        br {}
                        +"If you need help head over to the "
                        a {
                            href = "https://discord.com/channels/441805394323439646/443569023951568906"
                            target = WindowTarget._blank
                            rel = "noopener"
                            +"BSMG discord"
                        }
                        +"."
                    }
                    p {
                        className = ClassName("card-text")
                        +"WIP maps will be removed."
                    }
                }
            }
            div {
                className = ClassName("card bg-blue mb-3")
                div {
                    className = ClassName("card-body")
                    h4 {
                        className = ClassName("card-title")
                        +"AI Mapping"
                    }
                    p {
                        className = ClassName("card-text")
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
                className = ClassName("card bg-secondary mb-3")
                div {
                    className = ClassName("card-body")
                    p {
                        className = ClassName("card-text")
                        +"By uploading your map, you acknowledge that you agree to our "
                        a {
                            href = "/policy/tos"
                            +"Terms of Service"
                        }
                        +"."
                    }
                }
            }
        }
    }

    div {
        className = ClassName("row")
        if (!loading) {
            errors {
                validationErrors = errors
            }
        }
    }
}
