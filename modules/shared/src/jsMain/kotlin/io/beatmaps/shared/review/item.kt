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
import io.beatmaps.common.api.EIssueType
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
import react.use
import react.useEffect
import react.useRef
import react.useState
import web.cssom.ClassName
import web.html.HTMLDivElement
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
    val autoSize = useAutoSize<ReviewDetail, HTMLDivElement>(props, 2)

    val (featured, setFeatured) = useState<Boolean?>(null)
    val (sentiment, setSentiment) = useState<ReviewSentiment?>(null)
    val (newSentiment, setNewSentiment) = useState<ReviewSentiment?>(null)
    val (text, setText) = useState<String?>(null)

    val propReplies = props.obj?.replies.let { replies ->
        if (replies?.isNotEmpty() == true) replies else emptyList()
    }
    val (replies, setReplies) = useState(propReplies)
    val (editing, setEditing) = useState(false)

    val userData = use(globalContext)
    val modal = use(modalContext)
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
                    IssueCreationRequest(captcha, reason, reviewId, EIssueType.ReviewReport),
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
            className = ClassName("review-card")
            ref = autoSize.divRef
            autoSize.style(this)

            div {
                className = ClassName("main" + if (editing) " border-secondary" else "")
                sentimentIcon {
                    this.sentiment = sentimentLocal
                }

                div {
                    className = ClassName("content")
                    div {
                        className = ClassName("review-header")
                        reviewer {
                            reviewer = rv.creator
                            map = rv.map
                            time = rv.createdAt
                        }

                        if (featLocal) {
                            span {
                                className = ClassName("badge badge-success")
                                +"Featured"
                            }
                        }
                        if (userData != null) {
                            div {
                                className = ClassName("options")
                                // Curator/Admin gets to feature and delete
                                if (userData.curator) {
                                    div {
                                        className = ClassName("form-check form-switch d-inline-block")
                                        input {
                                            type = InputType.checkbox
                                            className = ClassName("form-check-input")
                                            checked = featLocal
                                            id = "featured-${rv.id}"
                                            onChange = {
                                                val current = it.currentTarget.checked
                                                curate(rv.id, current)
                                            }
                                        }
                                        label {
                                            className = ClassName("form-check-label")
                                            htmlFor = "featured-${rv.id}"
                                            +"Featured"
                                        }
                                    }
                                }
                                if (!userData.suspended && !userData.curator && props.userId != userData.userId) {
                                    a {
                                        href = "#"
                                        title = "Report"
                                        ariaLabel = "Report"
                                        onClick = {
                                            it.preventDefault()
                                            modal?.current?.showDialog?.invoke(
                                                ModalData(
                                                    "Report review",
                                                    bodyCallback = {
                                                        reportModal {
                                                            subject = "review"
                                                            this.reasonRef = reasonRef
                                                            this.captchaRef = captchaRef
                                                            errorsRef = errorRef
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
                                            className = ClassName("fas fa-flag text-danger")
                                        }
                                    }
                                }
                                if (!userData.suspended && (props.userId == userData.userId || userData.curator)) {
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
                                        ariaLabel = "Delete"
                                        onClick = {
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
                                                                className = ClassName("form-control")
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
                                            className = ClassName("fas fa-trash text-danger-light")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    div {
                        className = ClassName("review-body")
                        if (editing) {
                            sentimentPicker {
                                this.sentiment = newSentiment ?: sentimentLocal
                                updateSentiment = {
                                    setNewSentiment(it)
                                }
                            }
                        }
                        editableText {
                            this.text = text ?: rv.text
                            this.editing = editing
                            renderText = true
                            textClass = ClassName("mt-2")
                            maxLength = ReviewConstants.MAX_LENGTH
                            saveText = { newReview ->
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
                            stopEditing = { t ->
                                setText(t)
                                setEditing(false)
                            }
                        }

                        if (replies.any() && !editing) {
                            div {
                                className = ClassName("replies")
                                replies.forEach {
                                    reply {
                                        reply = it
                                        this.modal = modal
                                        captcha = props.captcha
                                    }
                                }
                            }
                        }

                        fun userCanReply() = userData != null && (userData.userId == rv.creator?.id || userData.userId == props.map?.uploader?.id ||
                            props.map?.collaborators?.any { it.id == userData.userId } == true)
                        if (!editing && props.captcha != null && userCanReply()) {
                            replyInput {
                                onSave = { reply ->
                                    props.captcha?.current?.execute()?.then {
                                        props.captcha?.current?.reset()
                                        Axios.post<ActionResponse>(
                                            "${Config.apibase}/reply/create/${rv.id}",
                                            ReplyRequest(reply, it),
                                            generateConfig<ReplyRequest, ActionResponse>(validStatus = arrayOf(200, 400))
                                        )
                                    }?.then { it }
                                }
                                onSuccess = {
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
            className = ClassName("review-card loading")
        }
    }
}
