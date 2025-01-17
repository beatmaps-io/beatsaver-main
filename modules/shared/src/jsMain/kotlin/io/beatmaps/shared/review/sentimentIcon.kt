package io.beatmaps.shared.review

import io.beatmaps.common.api.ReviewSentiment
import react.Props
import react.dom.html.ReactHTML.i
import react.fc
import web.cssom.ClassName

external interface SentimentIconProps : Props {
    var sentiment: ReviewSentiment
}

val sentimentIcon = fc<SentimentIconProps>("sentimentIcon") {
    val commonSentimentStyles = "fs-4 align-middle me-2 sentiment"
    when (it.sentiment) {
        ReviewSentiment.POSITIVE ->
            i {
                attrs.className = ClassName("fas fa-heart text-success $commonSentimentStyles")
            }
        ReviewSentiment.NEGATIVE ->
            i {
                attrs.className = ClassName("fas fa-heart-broken text-danger $commonSentimentStyles")
            }
        ReviewSentiment.NEUTRAL ->
            i {
                attrs.className = ClassName("far fa-heart text-warning $commonSentimentStyles")
            }
    }
}
