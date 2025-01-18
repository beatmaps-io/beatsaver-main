package io.beatmaps.issues

import external.AxiosResponse
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.IssueConstants
import io.beatmaps.maps.testplay.TimelineEntrySectionRenderer
import io.beatmaps.maps.testplay.timelineEntry
import io.beatmaps.shared.editableText
import io.beatmaps.shared.form.errors
import io.beatmaps.util.fcmemo
import io.beatmaps.util.useDidUpdateEffect
import react.PropsWithChildren
import react.useState
import kotlin.js.Promise

external interface NewCommentProps : PropsWithChildren {
    var loadingCallback: ((Boolean) -> Unit)?
    var saveCallback: (String) -> Promise<AxiosResponse<ActionResponse>>?
    var successCallback: (() -> Unit)?
    var buttonText: String
}

val newIssueComment = fcmemo<NewCommentProps>("newIssueComment") { props ->
    val (loading, setLoading) = useState(false)
    val (errors, setErrors) = useState(emptyList<String>())

    useDidUpdateEffect(loading) {
        props.loadingCallback?.invoke(loading)
    }

    timelineEntry {
        id = "new-comment"
        icon = "fa-plus"
        color = "success"
        bodyCallback = TimelineEntrySectionRenderer {
            editableText {
                buttonText = props.buttonText
                editing = true
                maxLength = IssueConstants.MAX_COMMENT_LENGTH
                saveText = { newText ->
                    props.saveCallback(newText)
                        ?.finally {
                            setLoading(false)
                        }
                }
                onError = {
                    setErrors(it)
                }
                stopEditing = {
                    props.successCallback?.invoke()
                }

                +props.children

                errors {
                    this.errors = errors
                }
            }
        }
    }
}
