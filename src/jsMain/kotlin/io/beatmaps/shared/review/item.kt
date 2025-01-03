package io.beatmaps.shared.review

import external.Axios
import external.axiosDelete
import external.axiosGet
import external.generateConfig
import external.reactFor
import io.beatmaps.Config
import io.beatmaps.History
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.CurateReview
import io.beatmaps.api.DeleteReview
import io.beatmaps.api.IssueCreationRequest
import io.beatmaps.api.MapDetail
import io.beatmaps.api.PutReview
import io.beatmaps.api.ReplyRequest
import io.beatmaps.api.ReviewConstants
import io.beatmaps.api.ReviewDetail
import io.beatmaps.api.ReviewReplyDetail
import io.beatmaps.captcha.ICaptchaHandler
import io.beatmaps.common.api.ReviewReportData
import io.beatmaps.common.api.ReviewSentiment
import io.beatmaps.globalContext
import io.beatmaps.index.ModalButton
import io.beatmaps.index.ModalCallbacks
import io.beatmaps.index.ModalData
import io.beatmaps.modreview.editableText
import io.beatmaps.shared.reviewer
import io.beatmaps.user.reportModal
import io.beatmaps.util.AutoSizeComponent
import io.beatmaps.util.AutoSizeComponentProps
import io.beatmaps.util.AutoSizeComponentState
import io.beatmaps.util.orCatch
import kotlinx.html.InputType
import kotlinx.html.id
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import react.MutableRefObject
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
import react.dom.span
import react.dom.textarea
import react.fc
import react.setState
import kotlin.js.Promise

external interface ReviewItemProps : AutoSizeComponentProps<ReviewDetail> {
    var userId: Int
    var map: MapDetail?
    var modal: RefObject<ModalCallbacks>?
    var captcha: RefObject<ICaptchaHandler>?
    var setExistingReview: ((Boolean) -> Unit)?
    var history: History?
}
external interface ReviewItemState : AutoSizeComponentState {
    var featured: Boolean?
    var sentiment: ReviewSentiment?
    var newSentiment: ReviewSentiment?
    var text: String?

    var replies: List<ReviewReplyDetail>?

    var editing: Boolean?
    var loading: Boolean?
}

external interface SentimentIconProps : Props {
    var sentiment: ReviewSentiment
}

val sentimentIcon = fc<SentimentIconProps> {
    val commonSentimentStyles = "fs-4 align-middle me-2 sentiment"
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
    private val captchaRef = createRef<ICaptchaHandler>().unsafeCast<MutableRefObject<ICaptchaHandler>>()
    private val errorRef = createRef<List<String>>().unsafeCast<MutableRefObject<List<String>>>()

    override fun componentWillReceiveProps(nextProps: ReviewItemProps) {
        if (props.obj != nextProps.obj) {
            state.replies = nextProps.obj?.replies
        }
    }

    private fun curate(id: Int, curated: Boolean = true) {
        setState {
            featured = curated
        }

        Axios.post<String>("${Config.apibase}/review/curate", CurateReview(id, curated), generateConfig<CurateReview, String>()).then({
            // Nothing
        }) { }
    }

    private fun delete(currentUser: Boolean) =
        (reasonRef.current?.value ?: "").let { reason ->
            reasonRef.current?.value = ""

            axiosDelete<DeleteReview, String>("${Config.apibase}/review/single/${props.map?.id}/${props.userId}", DeleteReview(reason)).then({
                setState {
                    sentiment = null
                    featured = null
                    text = null
                }
                hide()

                if (currentUser) props.setExistingReview?.invoke(false)
                true
            }) { false }
        }

    private fun report(reviewId: Int) =
        captchaRef.current?.let { cc ->
            cc.execute()?.then { captcha ->
                val reason = reasonRef.current?.value?.trim() ?: ""
                Axios.post<String>(
                    "${Config.apibase}/issues/create",
                    IssueCreationRequest(captcha, reason, ReviewReportData(reviewId)),
                    generateConfig<IssueCreationRequest, String>(validStatus = arrayOf(201))
                ).then {
                    props.history?.push("/issues/${it.data}")
                    true
                }
            }?.orCatch {
                errorRef.current = listOfNotNull(it.message)
                false
            }
        } ?: Promise.resolve(false)

    override fun RBuilder.render() {
        props.obj?.let { rv ->
            val featLocal = state.featured ?: (rv.curatedAt != null)
            val sentimentLocal = state.sentiment ?: rv.sentiment
            div("review-card") {
                ref = divRef
                style(this)

                div("main" + if (state.editing == true) " border-secondary" else "") {
                    sentimentIcon {
                        attrs.sentiment = sentimentLocal
                    }

                    div("content") {
                        div("review-header") {
                            reviewer {
                                attrs.reviewer = rv.creator
                                attrs.map = rv.map
                                attrs.time = rv.createdAt
                            }

                            if (featLocal) span("badge badge-success") { +"Featured" }

                            globalContext.Consumer { userData ->
                                if (userData != null) {
                                    div("options") {
                                        // Curator/Admin gets to feature and delete
                                        if (userData.curator) {
                                            div("form-check form-switch d-inline-block") {
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
                                        if (!userData.suspended && !userData.curator && props.userId != userData.userId) {
                                            a("#") {
                                                attrs.title = "Report"
                                                attrs.attributes["aria-label"] = "Report"
                                                attrs.onClickFunction = {
                                                    it.preventDefault()
                                                    if (state.loading != true) {
                                                        props.modal?.current?.showDialog?.invoke(
                                                            ModalData(
                                                                "Report review",
                                                                bodyCallback = {
                                                                    reportModal {
                                                                        attrs.subject = "review"
                                                                        attrs.reasonRef = reasonRef
                                                                        attrs.captchaRef = captchaRef
                                                                        attrs.errorsRef = errorRef
                                                                    }
                                                                },
                                                                buttons = listOf(
                                                                    ModalButton("Report", "danger") { report(rv.id) },
                                                                    ModalButton("Cancel")
                                                                )
                                                            )
                                                        )
                                                    }
                                                }
                                                i("fas fa-flag text-danger") { }
                                            }
                                        }
                                        if (!userData.suspended && (props.userId == userData.userId || userData.curator)) {
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
                                                    props.modal?.current?.showDialog?.invoke(
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
                                                            buttons = listOf(
                                                                ModalButton("YES, DELETE", "danger") { delete(userData.userId == props.userId) },
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
                        div("review-body") {
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
                                attrs.textClass = "mt-2"
                                attrs.maxLength = ReviewConstants.MAX_LENGTH
                                attrs.saveText = { newReview ->
                                    val newSentiment = state.newSentiment ?: sentimentLocal
                                    Axios.put<ActionResponse>("${Config.apibase}/review/single/${props.map?.id}/${props.userId}", PutReview(newReview, newSentiment), generateConfig<PutReview, ActionResponse>()).then { r ->
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

                            if (state.replies?.any() == true && state.editing != true) {
                                div("replies") {
                                    state.replies?.forEach {
                                        reply {
                                            attrs.reply = it
                                            attrs.modal = props.modal
                                            attrs.captcha = props.captcha
                                        }
                                    }
                                }
                            }

                            globalContext.Consumer { userData ->
                                if (state.editing != true && userData != null && (userData.userId == rv.creator?.id || userData.userId == props.map?.uploader?.id || props.map?.collaborators?.any { it.id == userData.userId } == true)) {
                                    replyInput {
                                        attrs.onSave = { reply ->
                                            props.captcha?.current?.execute()?.then {
                                                props.captcha?.current?.reset()
                                                Axios.post<ActionResponse>("${Config.apibase}/reply/create/${rv.id}", ReplyRequest(reply, it), generateConfig<ReplyRequest, ActionResponse>(validStatus = arrayOf(200, 400)))
                                            }?.then { it }
                                        }
                                        attrs.onSuccess = {
                                            axiosGet<ReviewDetail>("${Config.apibase}/review/single/${props.map?.id}/${props.userId}").then {
                                                setState {
                                                    replies = it.data.replies
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
        } ?: run {
            div("review-card loading") { }
        }
    }
}

fun RBuilder.reviewItem(handler: ReviewItemProps.() -> Unit) =
    child(ReviewItem::class) {
        this.attrs(handler)
    }
