package io.beatmaps.modreview

import external.Axios
import external.TimeAgo
import external.axiosDelete
import external.generateConfig
import external.routeLink
import io.beatmaps.Config
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.DeleteReview
import io.beatmaps.api.PutReview
import io.beatmaps.api.ReviewConstants
import io.beatmaps.api.ReviewDetail
import io.beatmaps.api.ReviewSentiment
import io.beatmaps.api.UserDetail
import io.beatmaps.index.ModalButton
import io.beatmaps.index.ModalComponent
import io.beatmaps.index.ModalData
import io.beatmaps.maps.review.sentimentIcon
import io.beatmaps.maps.review.sentimentPicker
import io.beatmaps.shared.mapTitle
import kotlinx.html.TD
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import org.w3c.dom.HTMLTextAreaElement
import react.Props
import react.RefObject
import react.createRef
import react.dom.RDOMBuilder
import react.dom.a
import react.dom.div
import react.dom.i
import react.dom.p
import react.dom.td
import react.dom.textarea
import react.dom.tr
import react.fc
import react.useState

external interface ModReviewEntryProps : Props {
    var modal: RefObject<ModalComponent>
    var entry: ReviewDetail?
    var setUser: (String) -> Unit
}

val modReviewEntryRenderer = fc<ModReviewEntryProps> {
    val reasonRef = createRef<HTMLTextAreaElement>()
    val (hidden, setHidden) = useState(false)
    val (editing, setEditing) = useState(false)
    val (sentiment, setSentiment) = useState(null as ReviewSentiment?)
    val (newSentiment, setNewSentiment) = useState(null as ReviewSentiment?)
    val (text, setText) = useState(null as String?)

    fun delete() {
        val reason = reasonRef.current?.value ?: ""
        reasonRef.current?.value = ""

        val mapId = it.entry?.map?.id
        val userId = it.entry?.creator?.id

        axiosDelete("${Config.apibase}/review/single/$mapId/$userId", DeleteReview(reason)).then({
            setHidden(true)
        }) { }
    }

    fun RDOMBuilder<TD>.linkUser(userDetail: UserDetail) {
        a("#", classes = "me-1") {
            attrs.onClickFunction = { ev ->
                ev.preventDefault()
                it.setUser(userDetail.name)
            }
            +userDetail.name
        }
        routeLink(userDetail.profileLink()) {
            i("fas fa-external-link-alt") {}
        }
    }

    if (!hidden) {
        tr {
            it.entry?.let { review ->
                td {
                    if (review.creator != null) linkUser(review.creator)
                }
                td {
                    if (review.map != null) mapTitle {
                        attrs.title = review.map.name
                        attrs.mapKey = review.map.id
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
                                it.modal.current?.showDialog(
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
                            i("fas fa-trash text-danger") { }
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
                it.entry?.let { review ->
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
                                this.text = text ?: review.text
                                this.editing = editing
                                maxLength = ReviewConstants.MAX_LENGTH
                                saveText = { newReview ->
                                    val newSentimentLocal = newSentiment ?: sentiment ?: review.sentiment
                                    Axios.put<ActionResponse>("${Config.apibase}/review/single/${review.map?.id}/${review.creator?.id}", PutReview(newReview, newSentimentLocal), generateConfig<PutReview, ActionResponse>()).then { r ->
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
