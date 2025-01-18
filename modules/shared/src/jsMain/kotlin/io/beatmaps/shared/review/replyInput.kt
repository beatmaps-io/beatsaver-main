package io.beatmaps.shared.review

import external.AxiosResponse
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.ReviewConstants
import io.beatmaps.shared.editableText
import io.beatmaps.shared.form.errors
import io.beatmaps.util.fcmemo
import react.Props
import react.dom.html.ReactHTML.div
import react.useState
import web.cssom.ClassName
import kotlin.js.Promise

external interface ReplyInputProps : Props {
    var onSave: ((String) -> Promise<AxiosResponse<ActionResponse>>?)?
    var onSuccess: ((String) -> Unit)?
}

val replyInput = fcmemo<ReplyInputProps>("replyInput") { props ->
    val (errors, setErrors) = useState(emptyList<String>())
    div {
        className = ClassName("reply-input")
        errors {
            this.errors = errors
        }
        editableText {
            placeholder = "Reply..."
            buttonText = "Reply"
            editing = true
            rows = 1
            maxLength = ReviewConstants.MAX_REPLY_LENGTH
            onError = {
                setErrors(it)
            }
            saveText = props.onSave
            stopEditing = props.onSuccess
        }
    }
}
