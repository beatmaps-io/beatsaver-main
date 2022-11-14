package io.beatmaps.modreview

import external.TimeAgo
import external.axiosDelete
import io.beatmaps.api.DeleteReview
import io.beatmaps.api.ReviewDetail
import io.beatmaps.api.UserDetail
import io.beatmaps.common.Config
import io.beatmaps.index.ModalButton
import io.beatmaps.index.ModalComponent
import io.beatmaps.index.ModalData
import io.beatmaps.maps.review.sentimentIcon
import io.beatmaps.shared.mapTitle
import kotlinx.html.TD
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import org.w3c.dom.HTMLTextAreaElement
import react.RProps
import react.RReadableRef
import react.createRef
import react.dom.RDOMBuilder
import react.dom.a
import react.dom.div
import react.dom.i
import react.dom.p
import react.dom.td
import react.dom.textarea
import react.dom.tr
import react.functionComponent
import react.router.dom.routeLink
import react.useState

external interface ModReviewEntryProps : RProps {
    var modal: RReadableRef<ModalComponent>
    var entry: ReviewDetail?
    var setUser: (String) -> Unit
}

val modReviewEntryRenderer = functionComponent<ModReviewEntryProps> {
    val reasonRef = createRef<HTMLTextAreaElement>()
    val (hidden, setHidden) = useState(false)

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
        routeLink("/profile/${userDetail.id}") {
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
                        attrs.sentiment = review.sentiment
                    }
                    +review.sentiment.name
                }
                td {
                    TimeAgo.default {
                        attrs.date = review.createdAt.toString()
                    }
                }
                td("action-cell") {
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
            } ?: run {
                td {
                    attrs.colSpan = "5"
                }
            }
        }
        tr("hiddenRow") {
            td {
                attrs.colSpan = "5"
                it.entry?.let {
                    div("text-wrap expand") {
                        p("card-text") {
                            +it.text
                        }
                    }
                }
            }
        }
    }
}
