package io.beatmaps.shared.review

import external.AxiosResponse
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.ReviewConstants
import io.beatmaps.shared.editableText
import io.beatmaps.shared.form.errors
import react.Props
import react.dom.div
import react.fc
import react.useState
import kotlin.js.Promise

external interface ReplyInputProps : Props {
    var onSave: ((String) -> Promise<AxiosResponse<ActionResponse>>?)?
    var onSuccess: ((String) -> Unit)?
}

val replyInput = fc<ReplyInputProps>("replyInput") { props ->
    val (errors, setErrors) = useState(emptyList<String>())
    div("reply-input") {
        errors {
            attrs.errors = errors
        }
        editableText {
            attrs.placeholder = "Reply..."
            attrs.buttonText = "Reply"
            attrs.textClass = ""
            attrs.editing = true
            attrs.rows = 1
            attrs.maxLength = ReviewConstants.MAX_REPLY_LENGTH
            attrs.onError = {
                setErrors(it)
            }
            attrs.saveText = props.onSave
            attrs.stopEditing = props.onSuccess
        }
    }
}
