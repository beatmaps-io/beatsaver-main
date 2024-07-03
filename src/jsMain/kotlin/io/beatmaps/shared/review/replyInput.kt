package io.beatmaps.shared.review

import external.AxiosResponse
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.ReviewConstants
import io.beatmaps.modreview.editableText
import react.Props
import react.dom.div
import react.fc
import kotlin.js.Promise

external interface ReplyInputProps : Props {
    var onSave: ((String) -> Promise<AxiosResponse<ActionResponse>>?)?
    var onSuccess: ((String) -> Unit)?
}

val replyInput = fc<ReplyInputProps> { props ->
    div("reply-input") {
        editableText {
            attrs.placeholder = "Reply..."
            attrs.buttonText = "Reply"
            attrs.editing = true
            attrs.rows = 1
            attrs.maxLength = ReviewConstants.MAX_REPLY_LENGTH
            attrs.saveText = props.onSave
            attrs.stopEditing = props.onSuccess
        }
    }
}
