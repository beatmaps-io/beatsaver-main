package io.beatmaps.shared.review

import io.beatmaps.common.api.ReviewSentiment
import react.Props
import react.dom.i
import react.fc

external interface SentimentIconProps : Props {
    var sentiment: ReviewSentiment
}

val sentimentIcon = fc<SentimentIconProps> {
    val commonSentimentStyles = "fs-4 align-middle me-2 sentiment"
    when (it.sentiment) {
        ReviewSentiment.POSITIVE ->
            i("fas fa-heart text-success $commonSentimentStyles") {}
        ReviewSentiment.NEGATIVE ->
            i("fas fa-heart-broken text-danger $commonSentimentStyles") {}
        ReviewSentiment.NEUTRAL ->
            i("far fa-heart text-warning $commonSentimentStyles") {}
    }
}