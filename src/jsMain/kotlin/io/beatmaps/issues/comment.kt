package io.beatmaps.issues

import external.Axios
import external.TimeAgo
import external.generateConfig
import external.routeLink
import io.beatmaps.Config
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.IssueCommentDetail
import io.beatmaps.api.IssueCommentRequest
import io.beatmaps.api.IssueConstants
import io.beatmaps.globalContext
import io.beatmaps.maps.testplay.TimelineEntrySectionRenderer
import io.beatmaps.maps.testplay.timelineEntry
import io.beatmaps.modreview.editableText
import io.beatmaps.shared.form.errors
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import react.Props
import react.RBuilder
import react.dom.a
import react.dom.div
import react.dom.i
import react.dom.span
import react.fc
import react.useContext
import react.useState
import kotlin.js.Promise

external interface IssueCommentProps : Props {
    var issueOpen: Boolean
    var issueId: Int
    var comment: IssueCommentDetail
}

val issueComment = fc<IssueCommentProps>("issueComment") { props ->
    val (editing, setEditing) = useState(false)
    val (loading, setLoading) = useState(false)
    val (errors, setErrors) = useState(emptyList<String>())
    val (public, setPublic) = useState(props.comment.public)
    val (text, setText) = useState(props.comment.text)
    val userData = useContext(globalContext)

    timelineEntry {
        attrs.className = "comment"
        attrs.icon = "fa-comments"
        attrs.color = "primary"
        attrs.headerCallback = TimelineEntrySectionRenderer {
            span {
                routeLink(props.comment.user.profileLink()) {
                    +props.comment.user.name
                }
                +" - "
                TimeAgo.default {
                    attrs.date = props.comment.createdAt.toString()
                }
            }

            div("link-buttons") {
                if (props.issueOpen && userData?.userId == props.comment.user.id) {
                    a("#") {
                        attrs.title = "Edit"
                        attrs.onClickFunction = { ev ->
                            ev.preventDefault()
                            setEditing(!editing)
                        }

                        i("fas fa-pen text-warning") { }
                    }
                }

                fun RBuilder.icon() = i("fas text-${if (public) "info fa-un" else "danger-light fa-"}lock") { }

                if (userData?.admin == true && props.issueOpen && !loading) {
                    a("#") {
                        attrs.title = if (public) "Lock" else "Unlock"
                        attrs.onClickFunction = { ev ->
                            ev.preventDefault()
                            setLoading(true)
                            val newPublic = !public
                            Axios.put<ActionResponse>(
                                "${Config.apibase}/issues/comments/${props.issueId}/${props.comment.id}",
                                IssueCommentRequest(public = newPublic),
                                generateConfig<IssueCommentRequest, ActionResponse>()
                            ).then {
                                setPublic(newPublic)
                            }.finally {
                                setLoading(false)
                            }
                        }

                        icon()
                    }
                } else {
                    icon()
                }
            }
        }
        attrs.bodyCallback = TimelineEntrySectionRenderer {
            editableText {
                attrs.editing = editing
                attrs.btnClass = "btn-success mt-1"
                attrs.text = text
                attrs.buttonText = "Edit comment"
                attrs.maxLength = IssueConstants.MAX_COMMENT_LENGTH
                attrs.onError = {
                    setErrors(it)
                }
                attrs.saveText = { newText ->
                    if (text != newText) {
                        Axios.put(
                            "${Config.apibase}/issues/comments/${props.issueId}/${props.comment.id}",
                            IssueCommentRequest(text = newText),
                            generateConfig<IssueCommentRequest, ActionResponse>()
                        )
                    } else {
                        Promise.reject(IllegalStateException("Comment unchanged"))
                    }
                }
                attrs.stopEditing = { newText ->
                    setText(newText)
                    setEditing(false)
                    setErrors(emptyList())
                }

                errors {
                    attrs.errors = errors
                }
            }
        }
    }
}
