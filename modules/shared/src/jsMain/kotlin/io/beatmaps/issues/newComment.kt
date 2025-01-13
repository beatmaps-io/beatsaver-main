package io.beatmaps.issues

import external.AxiosResponse
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.IssueConstants
import io.beatmaps.maps.testplay.TimelineEntrySectionRenderer
import io.beatmaps.maps.testplay.timelineEntry
import io.beatmaps.modreview.editableText
import io.beatmaps.shared.form.errors
import io.beatmaps.util.useDidUpdateEffect
import react.PropsWithChildren
import react.fc
import react.useState
import kotlin.js.Promise

external interface NewCommentProps : PropsWithChildren {
    var loadingCallback: ((Boolean) -> Unit)?
    var saveCallback: (String) -> Promise<AxiosResponse<ActionResponse>>?
    var successCallback: (() -> Unit)?
    var buttonText: String
}

val newIssueComment = fc<NewCommentProps>("newIssueComment") { props ->
    val (loading, setLoading) = useState(false)
    val (errors, setErrors) = useState(emptyList<String>())

    useDidUpdateEffect(loading) {
        props.loadingCallback?.invoke(loading)
    }

    timelineEntry {
        attrs.id = "new-comment"
        attrs.icon = "fa-plus"
        attrs.color = "success"
        attrs.bodyCallback = TimelineEntrySectionRenderer {
            editableText {
                attrs.buttonText = props.buttonText
                attrs.editing = true
                attrs.maxLength = IssueConstants.MAX_COMMENT_LENGTH
                attrs.saveText = { newText ->
                    props.saveCallback(newText)
                        ?.finally {
                            setLoading(false)
                        }
                }
                attrs.onError = {
                    setErrors(it)
                }
                attrs.stopEditing = {
                    props.successCallback?.invoke()
                }

                props.children()

                errors {
                    attrs.errors = errors
                }
            }
        }
    }
}
