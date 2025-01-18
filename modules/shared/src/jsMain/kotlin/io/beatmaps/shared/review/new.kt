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
import io.beatmaps.util.fcmemo
import react.Props
import react.RefObject
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.textarea
import react.useEffectOnce
import react.useRef
import react.useState
import web.cssom.ClassName
import web.html.HTMLTextAreaElement
import web.window.WindowTarget

external interface NewReviewProps : Props {
    var mapId: String
    var userId: Int
    var existingReview: Boolean?
    var captcha: RefObject<ICaptchaHandler>?
    var setExistingReview: ((Boolean) -> Unit)?
    var reloadList: (() -> Unit)?
}

val newReview = fcmemo<NewReviewProps>("newReview") { props ->
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
            className = ClassName("card mb-2")
            div {
                className = ClassName("card-body")
                sentimentPicker {
                    this.sentiment = sentiment
                    updateSentiment = {
                        setSentiment(it)
                    }
                }
                sentiment?.let { currentSentiment ->
                    p {
                        className = ClassName("my-2")
                        +"Comment (required)"
                        button {
                            className = ClassName("ms-1 fas fa-info-circle fa-button")
                            onClick = {
                                setFocusIcon(!focusIcon)
                            }
                        }
                        div {
                            className = ClassName("tooltip fade" + if (focusIcon) " show" else " d-none")
                            div {
                                className = ClassName("tooltip-arrow")
                            }
                            div {
                                className = ClassName("tooltip-inner")
                                +"Learn how to provide constructive map feedback "
                                a {
                                    href = "https://bsaber.com/how-to-write-constructive-map-reviews/"
                                    target = WindowTarget._blank
                                    +"here"
                                }
                                +"."
                                br {}
                                +"Reviews are subject to the "
                                a {
                                    href = "/policy/tos"
                                    target = WindowTarget._blank
                                    +"TOS"
                                }
                                +"."
                            }
                        }
                    }
                    textarea {
                        className = ClassName("form-control")
                        key = "description"
                        ref = textareaRef
                        id = "description"
                        rows = 5
                        maxLength = ReviewConstants.MAX_LENGTH
                        disabled = loading == true
                        onChange = {
                            setReviewLength(it.target.value.length)
                        }
                    }
                    span {
                        className = ClassName("badge badge-" + if (reviewLength > ReviewConstants.MAX_LENGTH - 20) "danger" else "dark")
                        id = "count_message"
                        +"$reviewLength / ${ReviewConstants.MAX_LENGTH}"
                    }
                    div {
                        className = ClassName("d-flex flex-row-reverse mt-1")
                        button {
                            className = ClassName("btn btn-info")
                            disabled = reviewLength < 1 || loading
                            onClick = {
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
                            this.errors = errors
                        }
                    }
                }
            }
        }
    }
}
