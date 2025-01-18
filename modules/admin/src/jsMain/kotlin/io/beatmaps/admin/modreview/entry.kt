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
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.textarea
import react.dom.html.ReactHTML.tr
import react.use
import react.useRef
import react.useState
import web.cssom.ClassName
import web.html.HTMLTextAreaElement
import kotlin.js.Promise

external interface ModReviewEntryProps : Props {
    var entry: CommentDetail?
    var setUser: (String) -> Unit
    var onDelete: (CommentDetail?, String) -> Promise<*>
    var onSave: (CommentDetail?, ReviewSentiment?, String) -> Promise<AxiosResponse<ActionResponse>>?
}

val modReviewEntry = fcmemo<ModReviewEntryProps>("modReviewEntry") { props ->
    val modal = use(modalContext)
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
                            user = c
                            callback = {
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
                            title = map.name
                            mapKey = map.id
                        }
                    }
                }
                td {
                    if (review is ReviewDetail) {
                        sentimentIcon {
                            this.sentiment = sentiment ?: review.sentiment
                        }
                        +(sentiment ?: review.sentiment).name
                    }
                }
                td {
                    TimeAgo.default {
                        date = review.createdAt.toString()
                    }
                }
                td {
                    className = ClassName("action-cell")
                    div {
                        className = ClassName("d-flex link-buttons")
                        a {
                            href = "#"

                            title = "Edit"
                            ariaLabel = "Edit"
                            onClick = { e ->
                                e.preventDefault()
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
                            onClick = { e ->
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
                                                className = ClassName("form-control")
                                                ref = reasonRef
                                            }
                                        },
                                        buttons = listOf(ModalButton("YES, DELETE", "danger", ::delete), ModalButton("Cancel"))
                                    )
                                )
                            }
                            i {
                                className = ClassName("fas fa-trash text-danger-light")
                            }
                        }
                    }
                }
            } ?: run {
                td {
                    colSpan = 5
                }
            }
        }
        tr {
            className = ClassName("hiddenRow")
            td {
                colSpan = 5
                props.entry?.let { review ->
                    div {
                        className = ClassName("text-wrap text-break expand")
                        p {
                            className = ClassName("card-text")
                            val textClass = if (editing && review is ReviewDetail) {
                                sentimentPicker {
                                    this.sentiment = newSentiment ?: sentiment ?: review.sentiment
                                    updateSentiment = { newSentiment ->
                                        setNewSentiment(newSentiment)
                                    }
                                }

                                ClassName("mt-2")
                            } else {
                                null
                            }

                            editableText {
                                this.text = text ?: review.text
                                this.editing = editing
                                maxLength = ReviewConstants.MAX_LENGTH
                                this.textClass = textClass
                                saveText = { newReview ->
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
                                stopEditing = { t ->
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
