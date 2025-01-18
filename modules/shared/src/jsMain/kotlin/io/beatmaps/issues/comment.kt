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
import io.beatmaps.shared.editableText
import io.beatmaps.shared.form.errors
import io.beatmaps.util.fcmemo
import react.ChildrenBuilder
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.span
import react.use
import react.useState
import web.cssom.ClassName
import kotlin.js.Promise

external interface IssueCommentProps : Props {
    var issueOpen: Boolean
    var issueId: Int
    var comment: IssueCommentDetail
}

val issueComment = fcmemo<IssueCommentProps>("issueComment") { props ->
    val (editing, setEditing) = useState(false)
    val (loading, setLoading) = useState(false)
    val (errors, setErrors) = useState(emptyList<String>())
    val (public, setPublic) = useState(props.comment.public)
    val (text, setText) = useState(props.comment.text)
    val userData = use(globalContext)

    timelineEntry {
        className = "comment"
        icon = "fa-comments"
        color = "primary"
        headerClass = "d-flex"
        headerCallback = TimelineEntrySectionRenderer {
            span {
                routeLink(props.comment.user.profileLink()) {
                    +props.comment.user.name
                }
                +" - "
                TimeAgo.default {
                    date = props.comment.createdAt.toString()
                }
            }

            div {
                className = ClassName("link-buttons")
                if (props.issueOpen && userData?.userId == props.comment.user.id) {
                    a {
                        href = "#"
                        title = "Edit"
                        onClick = { ev ->
                            ev.preventDefault()
                            setEditing(!editing)
                        }

                        i {
                            className = ClassName("fas fa-pen text-warning")
                        }
                    }
                }

                fun ChildrenBuilder.icon() = i { className = ClassName("fas text-${if (public) "info fa-un" else "danger-light fa-"}lock") }

                if (userData?.curator == true && props.issueOpen && !loading) {
                    a {
                        href = "#"
                        title = if (public) "Lock" else "Unlock"
                        onClick = { ev ->
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
        bodyCallback = TimelineEntrySectionRenderer {
            editableText {
                this.editing = editing
                btnClass = "btn-success mt-1"
                this.text = text
                renderText = true
                buttonText = "Edit comment"
                maxLength = IssueConstants.MAX_COMMENT_LENGTH
                onError = {
                    setErrors(it)
                }
                saveText = { newText ->
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
                stopEditing = { newText ->
                    setText(newText)
                    setEditing(false)
                    setErrors(emptyList())
                }

                errors {
                    this.errors = errors
                }
            }
        }
    }
}
