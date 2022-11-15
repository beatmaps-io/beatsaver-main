package io.beatmaps.maps.review

import io.beatmaps.api.ReviewSentiment
import kotlinx.html.js.onClickFunction
import react.RProps
import react.dom.button
import react.functionComponent

external interface SentimentProps : RProps {
    var sentiment: ReviewSentiment?
    var updateSentiment: ((ReviewSentiment) -> Unit)?
}

val sentimentPicker = functionComponent<SentimentProps> {
    fun renderSentiment(sentiment: ReviewSentiment, text: String, color: String) =
        button(classes = "btn btn-sm me-2 " + if (it.sentiment == sentiment) "btn-$color" else "btn-outline-$color") {
            attrs.onClickFunction = { e ->
                e.preventDefault()
                it.updateSentiment?.invoke(sentiment)
            }
            +text
        }

    renderSentiment(ReviewSentiment.POSITIVE, "I recommend this map", "success")
    renderSentiment(ReviewSentiment.NEUTRAL, "I have mixed feelings about this map", "warning")
    renderSentiment(ReviewSentiment.NEGATIVE, "I don't recommend this map", "danger")
}
