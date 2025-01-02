package io.beatmaps.shared.review

import external.Axios
import external.ICaptchaHandler
import external.axiosGet
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.PutReview
import io.beatmaps.api.ReviewConstants
import io.beatmaps.common.api.ReviewSentiment
import kotlinx.html.id
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLTextAreaElement
import react.Props
import react.RefObject
import react.dom.a
import react.dom.br
import react.dom.button
import react.dom.div
import react.dom.key
import react.dom.p
import react.dom.span
import react.dom.textarea
import react.fc
import react.useEffectOnce
import react.useRef
import react.useState

external interface NewReviewProps : Props {
    var mapId: String
    var userId: Int
    var existingReview: Boolean?
    var captcha: RefObject<ICaptchaHandler>?
    var setExistingReview: ((Boolean) -> Unit)?
    var reloadList: (() -> Unit)?
}

val newReview = fc<NewReviewProps> { props ->
    val (loading, setLoading) = useState(false)
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
        div("card mb-2") {
            div("card-body") {
                sentimentPicker {
                    attrs.sentiment = sentiment
                    attrs.updateSentiment = {
                        setSentiment(it)
                    }
                }
                sentiment?.let { currentSentiment ->
                    p("my-2") {
                        +"Comment (required)"
                        button(classes = "ms-1 fas fa-info-circle fa-button") {
                            attrs.onClickFunction = {
                                setFocusIcon(!focusIcon)
                            }
                        }
                        div("tooltip fade" + if (focusIcon) " show" else " d-none") {
                            div("tooltip-arrow") {}
                            div("tooltip-inner") {
                                +"Learn how to provide constructive map feedback "
                                a("https://bsaber.com/how-to-write-constructive-map-reviews/", target = "_blank") {
                                    +"here"
                                }
                                +"."
                                br {}
                                +"Reviews are subject to the "
                                a("/policy/tos", target = "_blank") {
                                    +"TOS"
                                }
                                +"."
                            }
                        }
                    }
                    textarea(classes = "form-control") {
                        attrs.key = "description"
                        ref = textareaRef
                        attrs.id = "description"
                        attrs.rows = "5"
                        attrs.maxLength = "${ReviewConstants.MAX_LENGTH}"
                        attrs.disabled = loading == true
                        attrs.onChangeFunction = {
                            setReviewLength((it.target as HTMLTextAreaElement).value.length)
                        }
                    }
                    span("badge badge-" + if (reviewLength > ReviewConstants.MAX_LENGTH - 20) "danger" else "dark") {
                        attrs.id = "count_message"
                        +"$reviewLength / ${ReviewConstants.MAX_LENGTH}"
                    }
                    button(classes = "btn btn-info mt-1 float-end") {
                        attrs.disabled = reviewLength < 1 || loading
                        attrs.onClickFunction = {
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
                                setLoading(false)

                                if (!r.data.success) return@then

                                setSentiment(null)
                                setReviewLength(0)
                                props.setExistingReview?.invoke(true)
                                props.reloadList?.invoke()
                            }) {
                                setLoading(false)
                            }
                        }
                        +"Leave Review"
                    }
                }
            }
        }
    }
}
