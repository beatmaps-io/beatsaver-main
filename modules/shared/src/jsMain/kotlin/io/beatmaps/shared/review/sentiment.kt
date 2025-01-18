package io.beatmaps.shared.review

import io.beatmaps.common.api.ReviewSentiment
import io.beatmaps.util.fcmemo
import react.ChildrenBuilder
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import web.cssom.ClassName

external interface SentimentProps : Props {
    var sentiment: ReviewSentiment?
    var updateSentiment: ((ReviewSentiment) -> Unit)?
}

val sentimentPicker = fcmemo<SentimentProps>("sentimentPicker") {
    fun ChildrenBuilder.renderSentiment(sentiment: ReviewSentiment, text: String, color: String) =
        button {
            className = ClassName("btn btn-sm " + if (it.sentiment == sentiment) "btn-$color" else "btn-outline-$color")
            onClick = { e ->
                e.preventDefault()
                it.updateSentiment?.invoke(sentiment)
            }
            +text
        }

    div {
        className = ClassName("s-pick")
        renderSentiment(ReviewSentiment.POSITIVE, "I recommend this map", "success")
        renderSentiment(ReviewSentiment.NEUTRAL, "I have mixed feelings about this map", "warning")
        renderSentiment(ReviewSentiment.NEGATIVE, "I don't recommend this map", "danger-light")
    }
}
