package io.beatmaps.shared.review

import external.Axios
import external.axiosDelete
import external.generateConfig
import external.reactFor
import io.beatmaps.Config
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.CurateReview
import io.beatmaps.api.DeleteReview
import io.beatmaps.api.PutReview
import io.beatmaps.api.ReviewConstants
import io.beatmaps.api.ReviewDetail
import io.beatmaps.common.api.ReviewSentiment
import io.beatmaps.globalContext
import io.beatmaps.index.ModalButton
import io.beatmaps.index.ModalComponent
import io.beatmaps.index.ModalData
import io.beatmaps.modreview.editableText
import io.beatmaps.shared.reviewer
import io.beatmaps.util.AutoSizeComponent
import io.beatmaps.util.AutoSizeComponentProps
import io.beatmaps.util.AutoSizeComponentState
import kotlinx.html.InputType
import kotlinx.html.id
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import react.Props
import react.RBuilder
import react.RefObject
import react.createRef
import react.dom.a
import react.dom.div
import react.dom.i
import react.dom.input
import react.dom.label
import react.dom.p
import react.dom.textarea
import react.fc
import react.setState

external interface ReviewItemProps : AutoSizeComponentProps<ReviewDetail> {
    var userId: Int
    var mapId: String
    var modal: RefObject<ModalComponent>?
    var setExistingReview: ((Boolean) -> Unit)?
}
external interface ReviewItemState : AutoSizeComponentState {
    var featured: Boolean?
    var sentiment: ReviewSentiment?
    var newSentiment: ReviewSentiment?
    var text: String?

    var editing: Boolean?
    var loading: Boolean?
}

external interface SentimentIconProps : Props {
    var sentiment: ReviewSentiment
}

val sentimentIcon = fc<SentimentIconProps> {
    val commonSentimentStyles = "fs-4 align-middle me-2"
    when (it.sentiment) {
        ReviewSentiment.POSITIVE ->
            i("fas fa-heart text-success $commonSentimentStyles") {}
        ReviewSentiment.NEGATIVE ->
            i("fas fa-heart-broken text-danger $commonSentimentStyles") {}
        ReviewSentiment.NEUTRAL ->
            i("far fa-heart text-warning $commonSentimentStyles") {}
    }
}

class ReviewItem : AutoSizeComponent<ReviewDetail, ReviewItemProps, ReviewItemState>(2) {
    private val reasonRef = createRef<HTMLTextAreaElement>()

    private fun curate(id: Int, curated: Boolean = true) {
        setState {
            featured = curated
        }

        Axios.post<String>("${Config.apibase}/review/curate", CurateReview(id, curated), generateConfig<CurateReview, String>()).then({
            // Nothing
        }) { }
    }

    private fun delete(currentUser: Boolean) {
        val reason = reasonRef.current?.value ?: ""
        reasonRef.current?.value = ""

        axiosDelete<DeleteReview, String>("${Config.apibase}/review/single/${props.mapId}/${props.userId}", DeleteReview(reason)).then({
            hide()

            if (currentUser) props.setExistingReview?.invoke(false)
        }) { }
    }

    override fun RBuilder.render() {
        props.obj?.let { rv ->
            val featLocal = state.featured ?: (rv.curatedAt != null)
            val sentimentLocal = state.sentiment ?: rv.sentiment
            div("review-card") {
                style(this)

                div("card" + if (featLocal) " border border-success" else "") {
                    ref = divRef

                    div("card-header d-flex") {
                        sentimentIcon {
                            attrs.sentiment = sentimentLocal
                        }
                        div(classes = "owner") {
                            reviewer {
                                attrs.reviewer = rv.creator
                                attrs.map = rv.map
                                attrs.time = rv.createdAt
                            }
                        }
                        globalContext.Consumer { userData ->
                            // Show tools if commenter or curator
                            if (userData != null && !userData.suspended && (props.userId == userData.userId || userData.curator)) {
                                div("ms-auto flex-shrink-0") {
                                    // Admin gets to feature and delete
                                    if (userData.curator) {
                                        div("form-check form-switch d-inline-block me-2") {
                                            input(InputType.checkBox, classes = "form-check-input") {
                                                attrs.checked = featLocal
                                                attrs.id = "featured-${rv.id}"
                                                attrs.onChangeFunction = {
                                                    val current = (it.currentTarget as HTMLInputElement).checked
                                                    curate(rv.id, current)
                                                }
                                            }
                                            label("form-check-label") {
                                                attrs.reactFor = "featured-${rv.id}"
                                                +"Featured"
                                            }
                                        }
                                    }
                                    a("#") {
                                        attrs.title = "Edit"
                                        attrs.attributes["aria-label"] = "Edit"
                                        attrs.onClickFunction = {
                                            it.preventDefault()
                                            setState {
                                                editing = editing != true
                                            }
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
                                                        if (props.userId != userData.userId) {
                                                            p {
                                                                +"Reason for action:"
                                                            }
                                                            textarea(classes = "form-control") {
                                                                ref = reasonRef
                                                            }
                                                        }
                                                    },
                                                    buttons = listOf(ModalButton("YES, DELETE", "danger") { delete(userData.userId == props.userId) }, ModalButton("Cancel"))
                                                )
                                            )
                                        }
                                        i("fas fa-trash text-danger-light") { }
                                    }
                                }
                            }
                        }
                    }
                    div("card-body") {
                        if (state.editing == true) {
                            sentimentPicker {
                                attrs.sentiment = state.newSentiment ?: sentimentLocal
                                attrs.updateSentiment = {
                                    setState {
                                        newSentiment = it
                                    }
                                }
                            }
                        }
                        editableText {
                            attrs.text = state.text ?: rv.text
                            attrs.editing = state.editing
                            attrs.renderText = true
                            attrs.maxLength = ReviewConstants.MAX_LENGTH
                            attrs.saveText = { newReview ->
                                val newSentiment = state.newSentiment ?: sentimentLocal
                                Axios.put<ActionResponse>("${Config.apibase}/review/single/${props.mapId}/${props.userId}", PutReview(newReview, newSentiment), generateConfig<PutReview, ActionResponse>()).then { r ->
                                    if (r.data.success) {
                                        setState {
                                            sentiment = newSentiment
                                        }
                                    }

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
        } ?: run {
            div("review-card loading") { }
        }
    }
}

fun RBuilder.reviewItem(handler: ReviewItemProps.() -> Unit) =
    child(ReviewItem::class) {
        this.attrs(handler)
    }
