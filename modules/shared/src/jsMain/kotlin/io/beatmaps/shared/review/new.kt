package io.beatmaps.shared.review

import external.Axios
import external.axiosGet
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.PutReview
import io.beatmaps.api.ReviewConstants
import io.beatmaps.captcha.ICaptchaHandler
import io.beatmaps.common.api.ReviewSentiment
import io.beatmaps.shared.form.errors
import org.w3c.dom.HTMLTextAreaElement
import react.Props
import react.RefObject
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.textarea
import react.fc
import react.useEffectOnce
import react.useRef
import react.useState
import web.cssom.ClassName
import web.window.WindowTarget

external interface NewReviewProps : Props {
    var mapId: String
    var userId: Int
    var existingReview: Boolean?
    var captcha: RefObject<ICaptchaHandler>?
    var setExistingReview: ((Boolean) -> Unit)?
    var reloadList: (() -> Unit)?
}

val newReview = fc<NewReviewProps>("newReview") { props ->
    val (loading, setLoading) = useState(false)
    val (errors, setErrors) = useState(emptyList<String>())
    val (sentiment, setSentiment) = useState<ReviewSentiment?>(null)
    val (focusIcon, setFocusIcon) = useState(false)
    val (reviewLength, setReviewLength) = useState(0)

    val textareaRef = useRef<HTMLTextAreaElement>()

    useEffectOnce {
        axiosGet<String>("${Config.apibase}/review/single/${props.mapId}/${props.userId}").then {
            props.setExistingReview?.invoke(true)
        }.catch {
            if (it.asDynamic().response?.status == 404) {
                props.setExistingReview?.invoke(false)
            }
        }
    }

    if (props.existingReview == false) {
        div {
            attrs.className = ClassName("card mb-2")
            div {
                attrs.className = ClassName("card-body")
                sentimentPicker {
                    attrs.sentiment = sentiment
                    attrs.updateSentiment = {
                        setSentiment(it)
                    }
                }
                sentiment?.let { currentSentiment ->
                    p {
                        attrs.className = ClassName("my-2")
                        +"Comment (required)"
                        button {
                            attrs.className = ClassName("ms-1 fas fa-info-circle fa-button")
                            attrs.onClick = {
                                setFocusIcon(!focusIcon)
                            }
                        }
                        div {
                            attrs.className = ClassName("tooltip fade" + if (focusIcon) " show" else " d-none")
                            div {
                                attrs.className = ClassName("tooltip-arrow")
                            }
                            div {
                                attrs.className = ClassName("tooltip-inner")
                                +"Learn how to provide constructive map feedback "
                                a {
                                    attrs.href = "https://bsaber.com/how-to-write-constructive-map-reviews/"
                                    attrs.target = WindowTarget._blank
                                    +"here"
                                }
                                +"."
                                br {}
                                +"Reviews are subject to the "
                                a {
                                    attrs.href = "/policy/tos"
                                    attrs.target = WindowTarget._blank
                                    +"TOS"
                                }
                                +"."
                            }
                        }
                    }
                    textarea {
                        attrs.className = ClassName("form-control")
                        key = "description"
                        ref = textareaRef
                        attrs.id = "description"
                        attrs.rows = 5
                        attrs.maxLength = ReviewConstants.MAX_LENGTH
                        attrs.disabled = loading == true
                        attrs.onChange = {
                            setReviewLength(it.target.value.length)
                        }
                    }
                    span {
                        attrs.className = ClassName("badge badge-" + if (reviewLength > ReviewConstants.MAX_LENGTH - 20) "danger" else "dark")
                        attrs.id = "count_message"
                        +"$reviewLength / ${ReviewConstants.MAX_LENGTH}"
                    }
                    div {
                        attrs.className = ClassName("d-flex flex-row-reverse mt-1")
                        button {
                            attrs.className = ClassName("btn btn-info")
                            attrs.disabled = reviewLength < 1 || loading
                            attrs.onClick = {
                                val newReview = textareaRef.current?.value ?: ""

                                setLoading(true)

                                props.captcha?.current?.execute()?.then { captcha ->
                                    props.captcha?.current?.reset()

                                    Axios.put<ActionResponse>(
                                        "${Config.apibase}/review/single/${props.mapId}/${props.userId}",
                                        PutReview(newReview, currentSentiment, captcha),
                                        generateConfig<PutReview, ActionResponse>()
                                    )
                                }?.then({ r ->
                                    setErrors(r.data.errors)

                                    if (!r.data.success) return@then

                                    setSentiment(null)
                                    setReviewLength(0)
                                    props.setExistingReview?.invoke(true)
                                    props.reloadList?.invoke()
                                }) {
                                    setErrors(listOfNotNull(it.message))
                                }?.finally {
                                    setLoading(false)
                                }
                            }
                            +"Leave Review"
                        }

                        errors {
                            attrs.errors = errors
                        }
                    }
                }
            }
        }
    }
}
