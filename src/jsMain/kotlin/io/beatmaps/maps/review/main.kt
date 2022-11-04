package io.beatmaps.maps.review

import external.Axios
import external.CancelTokenSource
import external.generateConfig
import io.beatmaps.api.ReviewConstants
import io.beatmaps.api.ReviewDetail
import io.beatmaps.api.ReviewSentiment
import io.beatmaps.api.ReviewsResponse
import io.beatmaps.common.Config
import io.beatmaps.globalContext
import io.beatmaps.shared.InfiniteScroll
import io.beatmaps.shared.InfiniteScrollElementRenderer
import kotlinx.html.id
import kotlinx.html.js.onBlurFunction
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import kotlinx.html.js.onFocusFunction
import org.w3c.dom.HTMLDivElement
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
import react.dom.value
import react.setState

external interface ReviewTableProps : RProps {
    var map: String
}

external interface ReviewTableState : RState {
    var resultsKey: Any
    var sentiment: ReviewSentiment?
    var description: String?
    var focusIcon: Boolean?
}

class ReviewTable : RComponent<ReviewTableProps, ReviewTableState>() {
    private val resultsTable = createRef<HTMLDivElement>()

    override fun componentWillUpdate(nextProps: ReviewTableProps, nextState: ReviewTableState) {
        if (props.map != nextProps.map) {
            nextState.apply {
                resultsKey = Any()
            }
        }
    }

    private fun getUrl(page: Int) = "${Config.apibase}/review/map/${props.map}/$page"

    private val loadPage = { toLoad: Int, token: CancelTokenSource ->
        Axios.get<ReviewsResponse>(
            getUrl(toLoad),
            generateConfig<String, ReviewsResponse>(token.token)
        ).then {
            return@then it.data.docs
        }
    }

    fun submitReviewForm(ctx: RBuilder) {
        ctx.div("card mb-2") {
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
                if (state.sentiment != null) {
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
                        attrs.id = "description"
                        attrs.value = state.description ?: ""
                        attrs.rows = "5"
                        attrs.maxLength = "${ReviewConstants.MAX_LENGTH}"
                        attrs.onChangeFunction = {
                            setState {
                                description = (it.target as HTMLTextAreaElement).value
                            }
                        }
                    }
                    val currentLength = state.description?.length ?: 0
                    span("badge badge-" + if (currentLength > ReviewConstants.MAX_LENGTH - 20) "danger" else "dark") {
                        attrs.id = "count_message"
                        +"$currentLength / ${ReviewConstants.MAX_LENGTH}"
                    }
                    button(classes = "btn btn-info mt-1 float-end") {
                        attrs.disabled = state.sentiment == null || currentLength < 1
                        +"Leave Review"
                    }
                }
            }
        }
    }

    override fun RBuilder.render() {
        div("reviews col-lg-8") {
            ref = resultsTable
            key = "resultsTable"

            globalContext.Consumer { userData ->
                if (userData != null) {
                    submitReviewForm(this)
                }
            }

            child(CommentsInfiniteScroll::class) {
                attrs.resultsKey = state.resultsKey
                attrs.rowHeight = 116.0
                attrs.itemsPerPage = 20
                attrs.container = resultsTable
                attrs.renderElement = InfiniteScrollElementRenderer { rv ->
                    reviewItem {
                        obj = rv
                    }
                }
                attrs.loadPage = loadPage
            }
        }
    }
}

class CommentsInfiniteScroll : InfiniteScroll<ReviewDetail>()

fun RBuilder.reviewTable(handler: ReviewTableProps.() -> Unit): ReactElement {
    return child(ReviewTable::class) {
        this.attrs(handler)
    }
}
