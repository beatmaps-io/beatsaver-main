package io.beatmaps.admin.modreview

import external.AxiosResponse
import external.TimeAgo
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.CommentDetail
import io.beatmaps.api.ReviewConstants
import io.beatmaps.api.ReviewDetail
import io.beatmaps.api.ReviewReplyDetail
import io.beatmaps.common.api.ReviewSentiment
import io.beatmaps.shared.ModalButton
import io.beatmaps.shared.ModalData
import io.beatmaps.shared.editableText
import io.beatmaps.shared.map.mapTitle
import io.beatmaps.shared.modalContext
import io.beatmaps.shared.review.sentimentIcon
import io.beatmaps.shared.review.sentimentPicker
import io.beatmaps.user.userLink
import io.beatmaps.util.fcmemo
import org.w3c.dom.HTMLTextAreaElement
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.textarea
import react.dom.html.ReactHTML.tr
import react.useContext
import react.useRef
import react.useState
import web.cssom.ClassName
import kotlin.js.Promise

external interface ModReviewEntryProps : Props {
    var entry: CommentDetail?
    var setUser: (String) -> Unit
    var onDelete: (CommentDetail?, String) -> Promise<*>
    var onSave: (CommentDetail?, ReviewSentiment?, String) -> Promise<AxiosResponse<ActionResponse>>?
}

val modReviewEntry = fcmemo<ModReviewEntryProps>("modReviewEntry") { props ->
    val modal = useContext(modalContext)
    val reasonRef = useRef<HTMLTextAreaElement>()
    val (hidden, setHidden) = useState(false)
    val (editing, setEditing) = useState(false)
    val (sentiment, setSentiment) = useState(null as ReviewSentiment?)
    val (newSentiment, setNewSentiment) = useState(null as ReviewSentiment?)
    val (text, setText) = useState(null as String?)

    fun delete(): Promise<Boolean> {
        val reason = reasonRef.current?.value ?: ""
        reasonRef.current?.value = ""

        return props.onDelete(props.entry, reason).then({
            setHidden(true)
            true
        }) { false }
    }

    if (!hidden) {
        tr {
            props.entry?.let { review ->
                td {
                    review.creator?.let { c ->
                        userLink {
                            attrs.user = c
                            attrs.callback = {
                                props.setUser(c.name)
                            }
                        }
                    }
                }
                td {
                    val map = when (review) {
                        is ReviewDetail -> review.map
                        is ReviewReplyDetail -> review.review?.map
                        else -> null
                    }

                    if (map != null) {
                        mapTitle {
                            attrs.title = map.name
                            attrs.mapKey = map.id
                        }
                    }
                }
                td {
                    if (review is ReviewDetail) {
                        sentimentIcon {
                            attrs.sentiment = sentiment ?: review.sentiment
                        }
                        +(sentiment ?: review.sentiment).name
                    }
                }
                td {
                    TimeAgo.default {
                        attrs.date = review.createdAt.toString()
                    }
                }
                td {
                    attrs.className = ClassName("action-cell")
                    div {
                        attrs.className = ClassName("d-flex link-buttons")
                        a {
                            attrs.href = "#"

                            attrs.title = "Edit"
                            attrs.ariaLabel = "Edit"
                            attrs.onClick = { e ->
                                e.preventDefault()
                                setEditing(!editing)
                            }
                            i {
                                attrs.className = ClassName("fas fa-pen text-warning")
                            }
                        }
                        a {
                            attrs.href = "#"

                            attrs.title = "Delete"
                            attrs.ariaLabel = "Delete"
                            attrs.onClick = { e ->
                                e.preventDefault()
                                modal?.current?.showDialog?.invoke(
                                    ModalData(
                                        "Delete review",
                                        bodyCallback = {
                                            p {
                                                +"Are you sure? This action cannot be reversed."
                                            }
                                            p {
                                                +"Reason for action:"
                                            }
                                            textarea {
                                                attrs.className = ClassName("form-control")
                                                ref = reasonRef
                                            }
                                        },
                                        buttons = listOf(ModalButton("YES, DELETE", "danger", ::delete), ModalButton("Cancel"))
                                    )
                                )
                            }
                            i {
                                attrs.className = ClassName("fas fa-trash text-danger-light")
                            }
                        }
                    }
                }
            } ?: run {
                td {
                    attrs.colSpan = 5
                }
            }
        }
        tr {
            attrs.className = ClassName("hiddenRow")
            td {
                attrs.colSpan = 5
                props.entry?.let { review ->
                    div {
                        attrs.className = ClassName("text-wrap text-break expand")
                        p {
                            attrs.className = ClassName("card-text")
                            val textClass = if (editing && review is ReviewDetail) {
                                sentimentPicker {
                                    attrs.sentiment = newSentiment ?: sentiment ?: review.sentiment
                                    attrs.updateSentiment = { newSentiment ->
                                        setNewSentiment(newSentiment)
                                    }
                                }

                                ClassName("mt-2")
                            } else {
                                null
                            }

                            editableText {
                                attrs.text = text ?: review.text
                                attrs.editing = editing
                                attrs.maxLength = ReviewConstants.MAX_LENGTH
                                attrs.textClass = textClass
                                attrs.saveText = { newReview ->
                                    val newSentimentLocal = if (review is ReviewDetail) {
                                        newSentiment ?: sentiment ?: review.sentiment
                                    } else {
                                        null
                                    }

                                    props.onSave(props.entry, newSentimentLocal, newReview)?.then { r ->
                                        if (r.data.success) setSentiment(newSentimentLocal)

                                        r
                                    }
                                }
                                attrs.stopEditing = { t ->
                                    setText(t)
                                    setEditing(false)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
