package io.beatmaps.maps.review

import io.beatmaps.api.ReviewDetail
import io.beatmaps.api.ReviewSentiment
import io.beatmaps.shared.playlistOwner
import io.beatmaps.util.AutoSizeComponent
import io.beatmaps.util.AutoSizeComponentProps
import io.beatmaps.util.AutoSizeComponentState
import react.RBuilder
import react.ReactElement
import react.dom.div
import react.dom.i

external interface ReviewItemProps : AutoSizeComponentProps<ReviewDetail>
external interface ReviewItemState : AutoSizeComponentState

class ReviewItem : AutoSizeComponent<ReviewDetail, ReviewItemProps, ReviewItemState>(2) {
    override fun RBuilder.render() {
        props.obj?.let { rv ->
            div("review-card") {
                style(this)

                div("card" + if (rv.curatedAt != null) " border border-success" else "") {
                    ref = divRef

                    div("card-header") {
                        val commonSentimentStyles = "fs-4 align-middle me-2"
                        when (rv.sentiment) {
                            ReviewSentiment.POSITIVE ->
                                i("fas fa-heart text-success $commonSentimentStyles") {}
                            ReviewSentiment.NEGATIVE ->
                                i("fas fa-heart-broken text-danger $commonSentimentStyles") {}
                            ReviewSentiment.NEUTRAL ->
                                i("far fa-heart text-warning $commonSentimentStyles") {}
                        }
                        playlistOwner {
                            attrs.owner = rv.creator
                            attrs.time = rv.createdAt
                        }
                    }
                    div("card-body") {
                        +rv.text
                    }
                }
            }
        } ?: run {
            div("review-card loading") { }
        }
    }
}

fun RBuilder.reviewItem(handler: ReviewItemProps.() -> Unit): ReactElement {
    return child(ReviewItem::class) {
        this.attrs(handler)
    }
}
