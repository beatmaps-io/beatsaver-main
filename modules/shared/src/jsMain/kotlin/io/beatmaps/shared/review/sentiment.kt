package io.beatmaps.shared.review

import io.beatmaps.common.api.ReviewSentiment
import kotlinx.html.js.onClickFunction
import react.Props
import react.RBuilder
import react.dom.button
import react.dom.div
import react.fc

external interface SentimentProps : Props {
    var sentiment: ReviewSentiment?
    var updateSentiment: ((ReviewSentiment) -> Unit)?
}

val sentimentPicker = fc<SentimentProps>("sentimentPicker") {
    fun RBuilder.renderSentiment(sentiment: ReviewSentiment, text: String, color: String) =
        button(classes = "btn btn-sm " + if (it.sentiment == sentiment) "btn-$color" else "btn-outline-$color") {
            attrs.onClickFunction = { e ->
                e.preventDefault()
                it.updateSentiment?.invoke(sentiment)
            }
            +text
        }

    div("s-pick") {
        renderSentiment(ReviewSentiment.POSITIVE, "I recommend this map", "success")
        renderSentiment(ReviewSentiment.NEUTRAL, "I have mixed feelings about this map", "warning")
        renderSentiment(ReviewSentiment.NEGATIVE, "I don't recommend this map", "danger-light")
    }
}
