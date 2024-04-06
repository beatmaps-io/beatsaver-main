package io.beatmaps.modreview

import external.Axios
import external.TimeAgo
import external.axiosDelete
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.DeleteReview
import io.beatmaps.api.PutReview
import io.beatmaps.api.ReviewConstants
import io.beatmaps.api.ReviewDetail
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
import react.createRef
import react.dom.a
import react.dom.div
import react.dom.i
import react.dom.p
import react.dom.td
import react.dom.textarea
import react.dom.tr
import react.fc
import react.useContext
import react.useState

external interface ModReviewEntryProps : Props {
    var entry: ReviewDetail?
    var setUser: (String) -> Unit
}

val modReviewEntryRenderer = fc<ModReviewEntryProps> { props ->
    val reasonRef = createRef<HTMLTextAreaElement>()
    val (hidden, setHidden) = useState(false)
    val (editing, setEditing) = useState(false)
    val (sentiment, setSentiment) = useState(null as ReviewSentiment?)
    val (newSentiment, setNewSentiment) = useState(null as ReviewSentiment?)
    val (text, setText) = useState(null as String?)

    val modal = useContext(modalContext)

    fun delete() {
        val reason = reasonRef.current?.value ?: ""
        reasonRef.current?.value = ""

        val mapId = props.entry?.map?.id
        val userId = props.entry?.creator?.id

        axiosDelete<DeleteReview, String>("${Config.apibase}/review/single/$mapId/$userId", DeleteReview(reason)).then({
            setHidden(true)
        }) { }
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
                    if (review.map != null) {
                        mapTitle {
                            attrs.title = review.map.name
                            attrs.mapKey = review.map.id
                        }
                    }
                }
                td {
                    sentimentIcon {
                        attrs.sentiment = sentiment ?: review.sentiment
                    }
                    +(sentiment ?: review.sentiment).name
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
                                setEditing(!editing)
                            }
                            i("fas fa-pen text-warning") { }
                        }
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
                            if (editing) {
                                sentimentPicker {
                                    attrs.sentiment = newSentiment ?: sentiment ?: review.sentiment
                                    attrs.updateSentiment = { newSentiment ->
                                        setNewSentiment(newSentiment)
                                    }
                                }
                            }
                            editableText {
                                attrs.text = text ?: review.text
                                attrs.editing = editing
                                attrs.maxLength = ReviewConstants.MAX_LENGTH
                                attrs.saveText = { newReview ->
                                    val newSentimentLocal = newSentiment ?: sentiment ?: review.sentiment
                                    Axios.put<ActionResponse>("${Config.apibase}/review/single/${review.map?.id}/${review.creator?.id}", PutReview(newReview, newSentimentLocal), generateConfig<PutReview, ActionResponse>()).then { r ->
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
