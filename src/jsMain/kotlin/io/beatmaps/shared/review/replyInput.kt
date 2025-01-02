package io.beatmaps.shared.review

import external.AxiosResponse
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.ReviewConstants
import io.beatmaps.modreview.editableText
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

val replyInput = fc<ReplyInputProps> { props ->
    val (errors, setErrors) = useState(listOf<String>())
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
            attrs.saveText = props.onSave?.let { f ->
                val newFunc = { newStr: String ->
                    f.invoke(newStr)?.then { res ->
                        if (!res.data.success) {
                            setErrors(res.data.errors)
                        }

                        res
                    }
                }

                newFunc
            }
            attrs.stopEditing = props.onSuccess
        }
    }
}
