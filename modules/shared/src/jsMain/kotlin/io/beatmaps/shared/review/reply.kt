package io.beatmaps.shared.review

import external.Axios
import external.axiosDelete
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.DeleteReview
import io.beatmaps.api.ReplyRequest
import io.beatmaps.api.ReviewConstants
import io.beatmaps.api.ReviewReplyDetail
import io.beatmaps.captcha.ICaptchaHandler
import io.beatmaps.globalContext
import io.beatmaps.shared.ModalButton
import io.beatmaps.shared.ModalCallbacks
import io.beatmaps.shared.ModalData
import io.beatmaps.shared.editableText
import io.beatmaps.shared.form.errors
import io.beatmaps.shared.reviewer
import io.beatmaps.util.fcmemo
import react.Props
import react.RefObject
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.textarea
import react.use
import react.useRef
import react.useState
import web.cssom.ClassName
import web.html.HTMLTextAreaElement

external interface ReplyProps : Props {
    var reply: ReviewReplyDetail
    var modal: RefObject<ModalCallbacks>?
    var captcha: RefObject<ICaptchaHandler>?
}

val reply = fcmemo<ReplyProps>("reply") { props ->
    val (editing, setEditing) = useState(false)
    val (text, setText) = useState(props.reply.text)
    val (deleted, setDeleted) = useState(props.reply.deletedAt != null)
    val (errors, setErrors) = useState(emptyList<String>())

    val reasonRef = useRef<HTMLTextAreaElement>()
    val userData = use(globalContext)

    fun delete() =
        (reasonRef.current?.value ?: "").let { reason ->
            reasonRef.current?.value = ""

            axiosDelete<DeleteReview, String>("${Config.apibase}/reply/single/${props.reply.id}", DeleteReview(reason)).then({
                setDeleted(true)
                true
            }) { false }
        }

    div {
        className = ClassName("reply")
        div {
            className = ClassName("reply-header")
            reviewer {
                reviewer = props.reply.creator
                time = props.reply.createdAt
            }
            if (!deleted) {
                // Show tools if commenter or curator
                if (userData != null && !userData.suspended && (props.reply.creator.id == userData.userId || userData.curator)) {
                    div {
                        className = ClassName("options")
                        a {
                            href = "#"

                            title = "Edit"
                            ariaLabel = "Edit"
                            onClick = {
                                it.preventDefault()
                                setEditing(!editing)
                            }
                            i {
                                className = ClassName("fas fa-pen text-warning")
                            }
                        }
                        a {
                            href = "#"

                            title = "Delete"
                            ariaLabel = "Delete"
                            onClick = {
                                it.preventDefault()
                                props.modal?.current?.showDialog?.invoke(
                                    ModalData(
                                        "Delete review",
                                        bodyCallback = {
                                            p {
                                                +"Are you sure? This action cannot be reversed."
                                            }
                                            if (props.reply.creator.id != userData.userId) {
                                                p {
                                                    +"Reason for action:"
                                                }
                                                textarea {
                                                    ref = reasonRef
                                                    className = ClassName("form-control")
                                                }
                                            }
                                        },
                                        buttons = listOf(
                                            ModalButton("YES, DELETE", "danger") { delete() },
                                            ModalButton("Cancel")
                                        )
                                    )
                                )
                            }
                            i {
                                className = ClassName("fas fa-trash text-danger-light")
                            }
                        }
                    }
                }
            }
        }

        div {
            className = ClassName("content")
            if (!deleted) {
                editableText {
                    this.text = text
                    this.editing = editing
                    renderText = true
                    textClass = ClassName("mt-2")
                    maxLength = ReviewConstants.MAX_REPLY_LENGTH
                    onError = {
                        setErrors(it)
                    }
                    saveText = { newReply ->
                        props.captcha?.current?.execute()?.then {
                            props.captcha?.current?.reset()

                            Axios.put<ActionResponse>("${Config.apibase}/reply/single/${props.reply.id}", ReplyRequest(newReply, it), generateConfig<ReplyRequest, ActionResponse>())
                        }?.then { it }
                    }
                    stopEditing = { t ->
                        setText(t)
                        setEditing(false)
                    }

                    errors {
                        this.errors = errors
                    }
                }
            } else {
                span {
                    className = ClassName("deleted")
                    +"This reply has been deleted."
                }
            }
        }
    }
}
