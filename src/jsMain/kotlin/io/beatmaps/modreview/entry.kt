package io.beatmaps.modreview

import external.AxiosResponse
import external.TimeAgo
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.CommentDetail
import io.beatmaps.api.ReviewConstants
import io.beatmaps.api.ReviewDetail
import io.beatmaps.api.ReviewReplyDetail
import io.beatmaps.common.api.ReviewSentiment
import io.beatmaps.index.ModalButton
import io.beatmaps.index.ModalData
import io.beatmaps.index.modalContext
import io.beatmaps.shared.map.mapTitle
import io.beatmaps.shared.review.sentimentIcon
import io.beatmaps.shared.review.sentimentPicker
import io.beatmaps.user.userLink
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import org.w3c.dom.HTMLTextAreaElement
import react.Props
import react.dom.a
import react.dom.div
import react.dom.i
import react.dom.p
import react.dom.td
import react.dom.textarea
import react.dom.tr
import react.fc
import react.useRef
import react.useState
import kotlin.js.Promise

external interface ModReviewEntryProps : Props {
    var entry: CommentDetail?
    var setUser: (String) -> Unit
    var onDelete: (String) -> Promise<*>
    var onSave: (ReviewSentiment?, String) -> Promise<AxiosResponse<ActionResponse>>?
}

val modReviewEntry = fc<ModReviewEntryProps> { props ->
    val reasonRef = useRef<HTMLTextAreaElement>()
    val (hidden, setHidden) = useState(false)
    val (editing, setEditing) = useState(false)
    val (sentiment, setSentiment) = useState(null as ReviewSentiment?)
    val (newSentiment, setNewSentiment) = useState(null as ReviewSentiment?)
    val (text, setText) = useState(null as String?)

    fun delete(): Promise<Boolean> {
        val reason = reasonRef.current?.value ?: ""
        reasonRef.current?.value = ""

        return props.onDelete(reason).then({
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
                td("action-cell") {
                    div("d-flex link-buttons") {
                        a("#") {
                            attrs.title = "Edit"
                            attrs.attributes["aria-label"] = "Edit"
                            attrs.onClickFunction = { e ->
                                e.preventDefault()
                                setEditing(!editing)
                            }
                            i("fas fa-pen text-warning") { }
                        }
                        modalContext.Consumer { modal ->
                            a("#") {
                                attrs.title = "Delete"
                                attrs.attributes["aria-label"] = "Delete"
                                attrs.onClickFunction = { e ->
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
                                                textarea(classes = "form-control") {
                                                    ref = reasonRef
                                                }
                                            },
                                            buttons = listOf(ModalButton("YES, DELETE", "danger", ::delete), ModalButton("Cancel"))
                                        )
                                    )
                                }
                                i("fas fa-trash text-danger-light") { }
                            }
                        }
                    }
                }
            } ?: run {
                td {
                    attrs.colSpan = "5"
                }
            }
        }
        tr("hiddenRow") {
            td {
                attrs.colSpan = "5"
                props.entry?.let { review ->
                    div("text-wrap text-break expand") {
                        p("card-text") {
                            val textClass = if (editing && review is ReviewDetail) {
                                sentimentPicker {
                                    attrs.sentiment = newSentiment ?: sentiment ?: review.sentiment
                                    attrs.updateSentiment = { newSentiment ->
                                        setNewSentiment(newSentiment)
                                    }
                                }

                                "mt-2"
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

                                    props.onSave(newSentimentLocal, newReview)?.then { r ->
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
