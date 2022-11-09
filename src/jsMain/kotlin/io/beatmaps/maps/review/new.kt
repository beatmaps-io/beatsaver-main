package io.beatmaps.maps.review

import external.Axios
import external.ReCAPTCHA
import external.axiosGet
import external.generateConfig
import external.recaptcha
import io.beatmaps.api.PutReview
import io.beatmaps.api.ReviewConstants
import io.beatmaps.api.ReviewSentiment
import io.beatmaps.common.Config
import kotlinx.html.id
import kotlinx.html.js.onBlurFunction
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import kotlinx.html.js.onFocusFunction
import org.w3c.dom.HTMLTextAreaElement
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
import react.createRef
import react.dom.a
import react.dom.br
import react.dom.button
import react.dom.div
import react.dom.p
import react.dom.span
import react.dom.textarea
import react.setState

external interface NewReviewProps : RProps {
    var mapId: String
    var userId: Int
    var existingReview: Boolean?
    var setExistingReview: ((Boolean) -> Unit)?
    var reloadList: (() -> Unit)?
}

external interface NewReviewState : RState {
    var reviewLength: Int?
    var sentiment: ReviewSentiment?
    var focusIcon: Boolean?
    var loading: Boolean?
}

class NewReview : RComponent<NewReviewProps, NewReviewState>() {
    private val captchaRef = createRef<ReCAPTCHA>()
    private val textareaRef = createRef<HTMLTextAreaElement>()

    override fun componentDidMount() {
        axiosGet<String>("${Config.apibase}/review/single/${props.mapId}/${props.userId}").then {
            props.setExistingReview?.invoke(true)
        }.catch {
            if (it.asDynamic().response?.status == 404) {
                props.setExistingReview?.invoke(false)
            }
        }
    }

    override fun RBuilder.render() {
        recaptcha(captchaRef)

        if (props.existingReview == false) {
            div("card mb-2") {
                div("card-body") {
                    val commonClasses = "btn btn-sm me-2 "
                    button(classes = commonClasses + if (state.sentiment == ReviewSentiment.POSITIVE) "btn-success" else "btn-outline-success") {
                        attrs.onClickFunction = {
                            setState {
                                sentiment = ReviewSentiment.POSITIVE
                            }
                        }
                        +"I recommend this map"
                    }
                    button(classes = commonClasses + if (state.sentiment == ReviewSentiment.NEUTRAL) "btn-warning" else "btn-outline-warning") {
                        attrs.onClickFunction = {
                            setState {
                                sentiment = ReviewSentiment.NEUTRAL
                            }
                        }
                        +"I have mixed feelings about this map"
                    }
                    button(classes = commonClasses + if (state.sentiment == ReviewSentiment.NEGATIVE) "btn-danger" else "btn-outline-danger") {
                        attrs.onClickFunction = {
                            setState {
                                sentiment = ReviewSentiment.NEGATIVE
                            }
                        }
                        +"I don't recommend this map"
                    }
                    state.sentiment?.let { currentSentiment ->
                        p("my-2") {
                            +"Comment (required)"
                            button(classes = "ms-1 fas fa-info-circle fa-button") {
                                attrs.onFocusFunction = {
                                    setState {
                                        focusIcon = true
                                    }
                                }
                                attrs.onBlurFunction = {
                                    setState {
                                        focusIcon = false
                                    }
                                }
                            }
                            div("tooltip fade" + if (state.focusIcon == true) " show" else " d-none") {
                                div("tooltip-arrow") {}
                                div("tooltip-inner") {
                                    +"Learn how to provide constructive map feedback "
                                    a("#") {
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
                            key = "description"
                            ref = textareaRef
                            attrs.id = "description"
                            attrs.rows = "5"
                            attrs.maxLength = "${ReviewConstants.MAX_LENGTH}"
                            attrs.disabled = state.loading == true
                            attrs.onChangeFunction = {
                                setState {
                                    reviewLength = (it.target as HTMLTextAreaElement).value.length
                                }
                            }
                        }
                        val currentLength = state.reviewLength ?: 0
                        span("badge badge-" + if (currentLength > ReviewConstants.MAX_LENGTH - 20) "danger" else "dark") {
                            attrs.id = "count_message"
                            +"$currentLength / ${ReviewConstants.MAX_LENGTH}"
                        }
                        button(classes = "btn btn-info mt-1 float-end") {
                            attrs.disabled = state.sentiment == null || currentLength < 1 || state.loading == true
                            attrs.onClickFunction = {
                                val newReview = textareaRef.current?.value ?: ""

                                setState {
                                    loading = true
                                }

                                captchaRef.current?.executeAsync()?.then { captcha ->
                                    Axios.put<String>(
                                        "${Config.apibase}/review/single/${props.mapId}/${props.userId}",
                                        PutReview(newReview, currentSentiment, captcha),
                                        generateConfig<PutReview, String>()
                                    )
                                }?.then({
                                    setState {
                                        loading = false
                                        sentiment = null
                                        reviewLength = null
                                    }
                                    props.setExistingReview?.invoke(true)
                                    props.reloadList?.invoke()
                                }) {
                                    setState {
                                        loading = false
                                    }
                                }
                            }
                            +"Leave Review"
                        }
                    }
                }
            }
        }
    }
}

fun RBuilder.newReview(handler: NewReviewProps.() -> Unit): ReactElement {
    return child(NewReview::class) {
        this.attrs(handler)
    }
}
