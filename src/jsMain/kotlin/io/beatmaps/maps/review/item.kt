package io.beatmaps.maps.review

import external.Axios
import external.axiosDelete
import external.generateConfig
import io.beatmaps.api.CurateReview
import io.beatmaps.api.DeleteReview
import io.beatmaps.api.PutReview
import io.beatmaps.api.ReviewDetail
import io.beatmaps.api.ReviewSentiment
import io.beatmaps.common.Config
import io.beatmaps.globalContext
import io.beatmaps.index.ModalButton
import io.beatmaps.index.ModalComponent
import io.beatmaps.index.ModalData
import io.beatmaps.shared.playlistOwner
import io.beatmaps.util.AutoSizeComponent
import io.beatmaps.util.AutoSizeComponentProps
import io.beatmaps.util.AutoSizeComponentState
import io.beatmaps.util.textToContent
import kotlinx.html.InputType
import kotlinx.html.id
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import react.RBuilder
import react.RReadableRef
import react.ReactElement
import react.createRef
import react.dom.a
import react.dom.div
import react.dom.i
import react.dom.input
import react.dom.label
import react.dom.p
import react.dom.textarea
import react.setState

external interface ReviewItemProps : AutoSizeComponentProps<ReviewDetail> {
    var userId: Int
    var mapId: String
    var modal: RReadableRef<ModalComponent>
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

class ReviewItem : AutoSizeComponent<ReviewDetail, ReviewItemProps, ReviewItemState>(2) {
    private val textareaRef = createRef<HTMLTextAreaElement>()
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

        axiosDelete("${Config.apibase}/review/single/${props.mapId}/${props.userId}", DeleteReview(reason)).then({
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
                        val commonSentimentStyles = "fs-4 align-middle me-2"
                        when (sentimentLocal) {
                            ReviewSentiment.POSITIVE ->
                                i("fas fa-heart text-success $commonSentimentStyles") {}
                            ReviewSentiment.NEGATIVE ->
                                i("fas fa-heart-broken text-danger $commonSentimentStyles") {}
                            ReviewSentiment.NEUTRAL ->
                                i("far fa-heart text-warning $commonSentimentStyles") {}
                        }
                        div(classes = "owner") {
                            playlistOwner {
                                attrs.owner = rv.creator
                                attrs.time = rv.createdAt
                            }
                        }
                        globalContext.Consumer { userData ->
                            // Show tools if commenter or curator
                            if (userData != null && (props.userId == userData.userId || userData.curator)) {
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
                                                attrs.htmlFor = "featured-${rv.id}"
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
                                            props.modal.current?.showDialog(
                                                ModalData(
                                                    "Delete review",
                                                    bodyCallback = {
                                                        p {
                                                            +"Are you sure? This action cannot be reversed."
                                                        }
                                                        if (userData.curator) {
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
                                        i("fas fa-trash text-danger") { }
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

                            textarea("10", classes = "form-control m-2") {
                                attrs.id = "review"
                                attrs.disabled = state.loading == true
                                +(state.text ?: rv.text)
                                ref = textareaRef
                            }

                            a(classes = "btn btn-primary m-1 float-end") {
                                attrs.onClickFunction = {
                                    val newReview = textareaRef.current?.asDynamic().value as String
                                    val newSentiment = state.newSentiment ?: sentimentLocal

                                    setState {
                                        loading = true
                                    }

                                    Axios.put<String>("${Config.apibase}/review/single/${props.mapId}/${props.userId}", PutReview(newReview, newSentiment), generateConfig<PutReview, String>()).then({
                                        setState {
                                            loading = false
                                            editing = false
                                            text = newReview
                                            sentiment = newSentiment
                                        }
                                    }) {
                                        setState {
                                            loading = false
                                        }
                                    }
                                }
                                +"Save"
                            }
                        } else {
                            textToContent(state.text ?: rv.text)
                        }
                    }
                }
            }
        } ?: run {
            div("review-card loading") { }
        }
    }
}

fun RBuilder.reviewItem(handler: ReviewItemProps.() -> Unit): ReactElement {
    return child(ReviewItem::class) {
        this.attrs(handler)
    }
}
