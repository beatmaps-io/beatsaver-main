package io.beatmaps.shared.review

import io.beatmaps.api.ReviewReplyDetail
import io.beatmaps.shared.reviewer
import react.Props
import react.dom.div
import react.dom.span
import react.fc

external interface ReplyProps : Props {
    var reply: ReviewReplyDetail
}

val reply = fc<ReplyProps> { props ->
    div("reply") {
        reviewer {
            attrs.reviewer = props.reply.user
            attrs.time = props.reply.createdAt
        }
        if (props.reply.deletedAt == null) span { +props.reply.text }
        else span("deleted") { +"This reply has been deleted." }
    }
}