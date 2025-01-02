package io.beatmaps.shared.review

import external.Axios
import external.IReCAPTCHA
import external.axiosDelete
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.DeleteReview
import io.beatmaps.api.ReplyRequest
import io.beatmaps.api.ReviewConstants
import io.beatmaps.api.ReviewReplyDetail
import io.beatmaps.globalContext
import io.beatmaps.index.ModalButton
import io.beatmaps.index.ModalComponent
import io.beatmaps.index.ModalData
import io.beatmaps.modreview.editableText
import io.beatmaps.shared.reviewer
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import org.w3c.dom.HTMLTextAreaElement
import react.Props
import react.RefObject
import react.dom.a
import react.dom.div
import react.dom.i
import react.dom.p
import react.dom.span
import react.dom.textarea
import react.fc
import react.useRef
import react.useState

external interface ReplyProps : Props {
    var reply: ReviewReplyDetail
    var modal: RefObject<ModalComponent>?
    var captcha: RefObject<IReCAPTCHA>?
}

val reply = fc<ReplyProps> { props ->
    val (editing, setEditing) = useState(false)
    val (text, setText) = useState(props.reply.text)
    val (deleted, setDeleted) = useState(props.reply.deletedAt != null)

    val reasonRef = useRef<HTMLTextAreaElement>()

    fun delete() {
        val reason = reasonRef.current?.value ?: ""
        reasonRef.current?.value = ""

        axiosDelete<DeleteReview, String>("${Config.apibase}/reply/single/${props.reply.id}", DeleteReview(reason)).then({
            setDeleted(true)
        }) { }
    }

    div("reply") {
        div("reply-header") {
            reviewer {
                attrs.reviewer = props.reply.creator
                attrs.time = props.reply.createdAt
            }
            if (!deleted) {
                globalContext.Consumer { userData ->
                    // Show tools if commenter or curator
                    if (userData != null && !userData.suspended && (props.reply.creator.id == userData.userId || userData.curator)) {
                        div("options") {
                            a("#") {
                                attrs.title = "Edit"
                                attrs.attributes["aria-label"] = "Edit"
                                attrs.onClickFunction = {
                                    it.preventDefault()
                                    setEditing(!editing)
                                }
                                i("fas fa-pen text-warning") { }
                            }
                            a("#") {
                                attrs.title = "Delete"
                                attrs.attributes["aria-label"] = "Delete"
                                attrs.onClickFunction = {
                                    it.preventDefault()
                                    props.modal?.current?.showDialog(
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
                                                    textarea(classes = "form-control") {
                                                        ref = reasonRef
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
                                i("fas fa-trash text-danger-light") { }
                            }
                        }
                    }
                }
            }
        }

        div("content") {
            if (!deleted) {
                editableText {
                    attrs.text = text
                    attrs.editing = editing
                    attrs.renderText = true
                    attrs.textClass = "mt-2"
                    attrs.maxLength = ReviewConstants.MAX_REPLY_LENGTH
                    attrs.saveText = { newReply ->
                        props.captcha?.current?.executeAsync()?.then {
                            props.captcha?.current?.reset()

                            Axios.put<ActionResponse>("${Config.apibase}/reply/single/${props.reply.id}", ReplyRequest(newReply, it), generateConfig<ReplyRequest, ActionResponse>())
                        }?.then { it }
                    }
                    attrs.stopEditing = { t ->
                        setText(t)
                        setEditing(false)
                    }
                }
            } else {
                span("deleted") { +"This reply has been deleted." }
            }
        }
    }
}
