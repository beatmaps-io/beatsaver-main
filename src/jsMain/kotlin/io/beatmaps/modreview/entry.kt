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
import react.RBuilder
import react.RComponent
import react.State
import react.createRef
import react.dom.a
import react.dom.div
import react.dom.i
import react.dom.p
import react.dom.td
import react.dom.textarea
import react.dom.tr
import react.setState
import kotlin.js.Promise

external interface ModReviewEntryProps<T : CommentDetail> : Props {
    var entry: T?
    var setUser: (String) -> Unit
    var onDelete: (String) -> Promise<*>
    var onSave: (ReviewSentiment?, String) -> Promise<AxiosResponse<ActionResponse>>?
}

external interface ModReviewEntryState : State {
    var hidden: Boolean
    var editing: Boolean
    var sentiment: ReviewSentiment?
    var newSentiment: ReviewSentiment?
    var text: String?
}

class ModReviewEntry<T : CommentDetail> : RComponent<ModReviewEntryProps<T>, ModReviewEntryState>() {
    private val reasonRef = createRef<HTMLTextAreaElement>()

    private fun delete() {
        val reason = reasonRef.current?.value ?: ""
        reasonRef.current?.value = ""

        props.onDelete(reason).then {
            setState {
                hidden = true
            }
        }
    }

    override fun RBuilder.render() {
        if (!state.hidden) {
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
                                attrs.sentiment = state.sentiment ?: review.sentiment
                            }
                            +(state.sentiment ?: review.sentiment).name
                        }
                    }
                    td {
                        TimeAgo.default {
                            attrs.date = review.createdAt.toString()
                        }
                    }
                    td("action-cell") {
                        div("d-flex") {
                            a("#") {
                                attrs.title = "Edit"
                                attrs.attributes["aria-label"] = "Edit"
                                attrs.onClickFunction = { e ->
                                    e.preventDefault()
                                    setState {
                                        editing = !state.editing
                                    }
                                }
                                i("fas fa-pen text-warning") { }
                            }
                            modalContext.Consumer { modal ->
                                a("#") {
                                    attrs.title = "Delete"
                                    attrs.attributes["aria-label"] = "Delete"
                                    attrs.onClickFunction = { e ->
                                        e.preventDefault()
                                        modal?.current?.showDialog(
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
                                if (state.editing && review is ReviewDetail) {
                                    sentimentPicker {
                                        attrs.sentiment = state.newSentiment ?: state.sentiment ?: review.sentiment
                                        attrs.updateSentiment = { newSentiment ->
                                            setState {
                                                this.newSentiment = newSentiment
                                            }
                                        }
                                    }
                                }
                                editableText {
                                    attrs.text = state.text ?: review.text
                                    attrs.editing = state.editing
                                    attrs.maxLength = ReviewConstants.MAX_LENGTH
                                    attrs.saveText = { newReview ->
                                        val newSentimentLocal = if (review is ReviewDetail) {
                                            state.newSentiment ?: state.sentiment ?: review.sentiment
                                        } else {
                                            null
                                        }

                                        props.onSave(newSentimentLocal, newReview)?.then { r ->
                                            if (r.data.success) setState { sentiment = newSentimentLocal }

                                            r
                                        }
                                    }
                                    attrs.stopEditing = { t ->
                                        setState {
                                            text = t
                                            editing = false
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun <T : CommentDetail> RBuilder.modReviewEntry(handler: ModReviewEntryProps<T>.() -> Unit) {
    return child((ModReviewEntry<T>())::class) {
        this.attrs(handler)
    }
}
