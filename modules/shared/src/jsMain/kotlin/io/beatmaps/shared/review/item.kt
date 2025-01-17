package io.beatmaps.shared.review

import external.Axios
import external.axiosDelete
import external.axiosGet
import external.generateConfig
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
import io.beatmaps.captcha.ICaptchaHandler
import io.beatmaps.common.api.ReviewReportData
import io.beatmaps.common.api.ReviewSentiment
import io.beatmaps.globalContext
import io.beatmaps.issues.reportModal
import io.beatmaps.shared.ModalButton
import io.beatmaps.shared.ModalData
import io.beatmaps.shared.editableText
import io.beatmaps.shared.modalContext
import io.beatmaps.shared.reviewer
import io.beatmaps.util.AutoSizeComponentProps
import io.beatmaps.util.fcmemo
import io.beatmaps.util.orCatch
import io.beatmaps.util.useAutoSize
import react.RefObject
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.textarea
import react.router.useNavigate
import react.useContext
import react.useEffect
import react.useRef
import react.useState
import web.cssom.ClassName
import web.html.HTMLTextAreaElement
import web.html.InputType
import kotlin.js.Promise

external interface ReviewItemProps : AutoSizeComponentProps<ReviewDetail> {
    var userId: Int
    var map: MapDetail?
    var captcha: RefObject<ICaptchaHandler>?
    var setExistingReview: ((Boolean) -> Unit)?
}

val reviewItem = fcmemo<ReviewItemProps>("reviewItem") { props ->
    val autoSize = useAutoSize(props, 2)

    val (featured, setFeatured) = useState<Boolean?>(null)
    val (sentiment, setSentiment) = useState<ReviewSentiment?>(null)
    val (newSentiment, setNewSentiment) = useState<ReviewSentiment?>(null)
    val (text, setText) = useState<String?>(null)

    val propReplies = props.obj?.replies.let { replies ->
        if (replies?.isNotEmpty() == true) replies else emptyList()
    }
    val (replies, setReplies) = useState(propReplies)
    val (editing, setEditing) = useState(false)

    val userData = useContext(globalContext)
    val modal = useContext(modalContext)
    val history = History(useNavigate())

    val reasonRef = useRef<HTMLTextAreaElement>()
    val captchaRef = useRef<ICaptchaHandler>()
    val errorRef = useRef<List<String>>()

    useEffect(propReplies) {
        setReplies(propReplies)
    }

    fun curate(id: Int, curated: Boolean = true) {
        setFeatured(curated)

        Axios.post<String>("${Config.apibase}/review/curate", CurateReview(id, curated), generateConfig<CurateReview, String>()).then({
            // Nothing
        }) { }
    }

    fun delete(currentUser: Boolean) =
        (reasonRef.current?.value ?: "").let { reason ->
            reasonRef.current?.value = ""

            axiosDelete<DeleteReview, String>("${Config.apibase}/review/single/${props.map?.id}/${props.userId}", DeleteReview(reason)).then({
                setSentiment(null)
                setFeatured(null)
                setText(null)
                autoSize.hide()

                if (currentUser) props.setExistingReview?.invoke(false)
                true
            }) { false }
        }

    fun report(reviewId: Int) =
        captchaRef.current?.let { cc ->
            cc.execute()?.then { captcha ->
                val reason = reasonRef.current?.value?.trim() ?: ""
                Axios.post<String>(
                    "${Config.apibase}/issues/create",
                    IssueCreationRequest(captcha, reason, ReviewReportData(reviewId)),
                    generateConfig<IssueCreationRequest, String>(validStatus = arrayOf(201))
                ).then {
                    history.push("/issues/${it.data}")
                    true
                }
            }?.orCatch {
                errorRef.current = listOfNotNull(it.message)
                false
            }
        } ?: Promise.resolve(false)

    props.obj?.let { rv ->
        val featLocal = featured ?: (rv.curatedAt != null)
        val sentimentLocal = sentiment ?: rv.sentiment
        div {
            attrs.className = ClassName("review-card")
            ref = autoSize.divRef
            autoSize.style(this)

            div {
                attrs.className = ClassName("main" + if (editing) " border-secondary" else "")
                sentimentIcon {
                    attrs.sentiment = sentimentLocal
                }

                div {
                    attrs.className = ClassName("content")
                    div {
                        attrs.className = ClassName("review-header")
                        reviewer {
                            attrs.reviewer = rv.creator
                            attrs.map = rv.map
                            attrs.time = rv.createdAt
                        }

                        if (featLocal) {
                            span {
                                attrs.className = ClassName("badge badge-success")
                                +"Featured"
                            }
                        }
                        if (userData != null) {
                            div {
                                attrs.className = ClassName("options")
                                // Curator/Admin gets to feature and delete
                                if (userData.curator) {
                                    div {
                                        attrs.className = ClassName("form-check form-switch d-inline-block")
                                        input {
                                            attrs.type = InputType.checkbox
                                            attrs.className = ClassName("form-check-input")
                                            attrs.checked = featLocal
                                            attrs.id = "featured-${rv.id}"
                                            attrs.onChange = {
                                                val current = it.currentTarget.checked
                                                curate(rv.id, current)
                                            }
                                        }
                                        label {
                                            attrs.className = ClassName("form-check-label")
                                            attrs.htmlFor = "featured-${rv.id}"
                                            +"Featured"
                                        }
                                    }
                                }
                                if (!userData.suspended && !userData.curator && props.userId != userData.userId) {
                                    a {
                                        attrs.href = "#"
                                        attrs.title = "Report"
                                        attrs.ariaLabel = "Report"
                                        attrs.onClick = {
                                            it.preventDefault()
                                            modal?.current?.showDialog?.invoke(
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
                                        i {
                                            attrs.className = ClassName("fas fa-flag text-danger")
                                        }
                                    }
                                }
                                if (!userData.suspended && (props.userId == userData.userId || userData.curator)) {
                                    a {
                                        attrs.href = "#"
                                        attrs.title = "Edit"
                                        attrs.ariaLabel = "Edit"
                                        attrs.onClick = {
                                            it.preventDefault()
                                            setEditing(!editing)
                                        }
                                        i {
                                            attrs.className = ClassName("fas fa-pen text-warning")
                                        }
                                    }
                                    a {
                                        attrs.href = "#"
                                        attrs.ariaLabel = "Delete"
                                        attrs.onClick = {
                                            it.preventDefault()
                                            modal?.current?.showDialog?.invoke(
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
                                                            textarea {
                                                                ref = reasonRef
                                                                attrs.className = ClassName("form-control")
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
                                        i {
                                            attrs.className = ClassName("fas fa-trash text-danger-light")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    div {
                        attrs.className = ClassName("review-body")
                        if (editing) {
                            sentimentPicker {
                                attrs.sentiment = newSentiment ?: sentimentLocal
                                attrs.updateSentiment = {
                                    setNewSentiment(it)
                                }
                            }
                        }
                        editableText {
                            attrs.text = text ?: rv.text
                            attrs.editing = editing
                            attrs.renderText = true
                            attrs.textClass = ClassName("mt-2")
                            attrs.maxLength = ReviewConstants.MAX_LENGTH
                            attrs.saveText = { newReview ->
                                val reqSentiment = newSentiment ?: sentimentLocal
                                Axios.put<ActionResponse>(
                                    "${Config.apibase}/review/single/${props.map?.id}/${props.userId}",
                                    PutReview(newReview, reqSentiment),
                                    generateConfig<PutReview, ActionResponse>()
                                ).then { r ->
                                    if (r.data.success) {
                                        setSentiment(reqSentiment)
                                    }

                                    r
                                }
                            }
                            attrs.stopEditing = { t ->
                                setText(t)
                                setEditing(false)
                            }
                        }

                        if (replies.any() && !editing) {
                            div {
                                attrs.className = ClassName("replies")
                                replies.forEach {
                                    reply {
                                        attrs.reply = it
                                        attrs.modal = modal
                                        attrs.captcha = props.captcha
                                    }
                                }
                            }
                        }

                        if (!editing && userData != null && (userData.userId == rv.creator?.id || userData.userId == props.map?.uploader?.id || props.map?.collaborators?.any { it.id == userData.userId } == true)) {
                            replyInput {
                                attrs.onSave = { reply ->
                                    props.captcha?.current?.execute()?.then {
                                        props.captcha?.current?.reset()
                                        Axios.post<ActionResponse>(
                                            "${Config.apibase}/reply/create/${rv.id}",
                                            ReplyRequest(reply, it),
                                            generateConfig<ReplyRequest, ActionResponse>(validStatus = arrayOf(200, 400))
                                        )
                                    }?.then { it }
                                }
                                attrs.onSuccess = {
                                    axiosGet<ReviewDetail>("${Config.apibase}/review/single/${props.map?.id}/${props.userId}").then {
                                        setReplies(it.data.replies)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } ?: run {
        div {
            attrs.className = ClassName("review-card loading")
        }
    }
}
