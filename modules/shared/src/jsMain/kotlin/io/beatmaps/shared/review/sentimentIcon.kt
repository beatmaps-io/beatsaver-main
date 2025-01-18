package io.beatmaps.shared.review

import io.beatmaps.common.api.ReviewSentiment
import io.beatmaps.util.fcmemo
import react.Props
import react.dom.html.ReactHTML.i
import web.cssom.ClassName

external interface SentimentIconProps : Props {
    var sentiment: ReviewSentiment
}

val sentimentIcon = fcmemo<SentimentIconProps>("sentimentIcon") {
    val commonSentimentStyles = "fs-4 align-middle me-2 sentiment"
    when (it.sentiment) {
        ReviewSentiment.POSITIVE ->
            i {
                className = ClassName("fas fa-heart text-success $commonSentimentStyles")
            }
        ReviewSentiment.NEGATIVE ->
            i {
                className = ClassName("fas fa-heart-broken text-danger $commonSentimentStyles")
            }
        ReviewSentiment.NEUTRAL ->
            i {
                className = ClassName("far fa-heart text-warning $commonSentimentStyles")
            }
    }
}
