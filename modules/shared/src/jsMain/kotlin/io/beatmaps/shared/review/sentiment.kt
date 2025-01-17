package io.beatmaps.shared.review

import io.beatmaps.common.api.ReviewSentiment
import react.Props
import react.RBuilder
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.fc
import web.cssom.ClassName

external interface SentimentProps : Props {
    var sentiment: ReviewSentiment?
    var updateSentiment: ((ReviewSentiment) -> Unit)?
}

val sentimentPicker = fc<SentimentProps>("sentimentPicker") {
    fun RBuilder.renderSentiment(sentiment: ReviewSentiment, text: String, color: String) =
        button {
            attrs.className = ClassName("btn btn-sm " + if (it.sentiment == sentiment) "btn-$color" else "btn-outline-$color")
            attrs.onClick = { e ->
                e.preventDefault()
                it.updateSentiment?.invoke(sentiment)
            }
            +text
        }

    div {
        attrs.className = ClassName("s-pick")
        renderSentiment(ReviewSentiment.POSITIVE, "I recommend this map", "success")
        renderSentiment(ReviewSentiment.NEUTRAL, "I have mixed feelings about this map", "warning")
        renderSentiment(ReviewSentiment.NEGATIVE, "I don't recommend this map", "danger-light")
    }
}
