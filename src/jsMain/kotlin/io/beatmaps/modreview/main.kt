package io.beatmaps.modreview

import external.Axios
import external.CancelTokenSource
import external.axiosDelete
import external.generateConfig
import external.routeLink
import io.beatmaps.Config
import io.beatmaps.History
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.DeleteReview
import io.beatmaps.api.PutReview
import io.beatmaps.api.RepliesResponse
import io.beatmaps.api.ReplyRequest
import io.beatmaps.api.ReviewDetail
import io.beatmaps.api.ReviewReplyDetail
import io.beatmaps.api.ReviewsResponse
import io.beatmaps.globalContext
import io.beatmaps.index.ModalCallbacks
import io.beatmaps.index.modal
import io.beatmaps.index.modalContext
import io.beatmaps.setPageTitle
import io.beatmaps.shared.InfiniteScroll
import io.beatmaps.shared.InfiniteScrollElementRenderer
import io.beatmaps.shared.review.CommentsInfiniteScroll
import kotlinx.dom.hasClass
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.url.URLSearchParams
import react.Props
import react.dom.button
import react.dom.form
import react.dom.input
import react.dom.table
import react.dom.tbody
import react.dom.td
import react.dom.th
import react.dom.thead
import react.dom.tr
import react.fc
import react.ref
import react.router.useLocation
import react.router.useNavigate
import react.useContext
import react.useEffect
import react.useEffectOnce
import react.useRef
import react.useState
import kotlin.reflect.KClass

external interface ModReviewProps : Props {
    var type: KClass<*>
}

val modReview = fc<ModReviewProps>("modReview") { props ->
    val userData = useContext(globalContext)
    val history = History(useNavigate())
    val location = useLocation()

    val resultsTable = useRef<HTMLElement>()
    val modalRef = useRef<ModalCallbacks>()
    val userRef = useRef<HTMLInputElement>()

    val userLocal = URLSearchParams(location.search).let { u ->
        u.get("user") ?: ""
    }

    val (user, setUser) = useState(userLocal)

    useEffectOnce {
        setPageTitle("Review Moderation")

        if (userData?.curator != true) {
            history.push("/")
        }
    }

    useEffect(location) {
        userRef.current?.value = userLocal

        setUser(userLocal)
    }

    fun urlExtension(): String {
        val params = listOfNotNull(
            userRef.current?.value?.let { if (it.isNotBlank()) "user=$it" else null }
        )

        return if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
    }

    modal {
        attrs.callbacks = modalRef
    }

    modalContext.Provider {
        attrs.value = modalRef

        form {
            table("table table-dark table-striped-3 modreview") {
                thead {
                    tr {
                        th { +"User" }
                        th { +"Map" }
                        th { +"Sentiment" }
                        th { +"Time" }
                        th { +"" }
                    }
                    tr {
                        td {
                            input(InputType.text, classes = "form-control") {
                                attrs.placeholder = "User"
                                attrs.attributes["aria-label"] = "User"
                                ref = userRef
                            }
                        }
                        td {
                            attrs.colSpan = "3"
                            button(type = ButtonType.submit, classes = "btn btn-primary") {
                                attrs.onClickFunction = {
                                    it.preventDefault()

                                    history.push(location.pathname + urlExtension())
                                }

                                +"Filter"
                            }
                        }
                        td {
                            when (props.type) {
                                ReviewDetail::class -> {
                                    routeLink("/modreply" + urlExtension(), "btn btn-primary") {
                                        +"Replies"
                                    }
                                }
                                ReviewReplyDetail::class -> {
                                    routeLink("/modreview" + urlExtension(), "btn btn-primary") {
                                        +"Reviews"
                                    }
                                }
                            }
                        }
                    }
                }
                tbody {
                    ref = resultsTable
                    key = "modreviewTable"

                    when (props.type) {
                        ReviewDetail::class -> {
                            val loadPage = { toLoad: Int, token: CancelTokenSource ->
                                Axios.get<ReviewsResponse>(
                                    "${Config.apibase}/review/latest/$toLoad" + urlExtension(),
                                    generateConfig<String, ReviewsResponse>(token.token)
                                ).then {
                                    return@then it.data.docs
                                }
                            }

                            child(CommentsInfiniteScroll::class) {
                                attrs.resultsKey = user
                                attrs.rowHeight = 95.5
                                attrs.itemsPerPage = 20
                                attrs.container = resultsTable
                                attrs.loadPage = loadPage
                                attrs.childFilter = {
                                    !it.hasClass("hiddenRow")
                                }
                                attrs.renderElement = InfiniteScrollElementRenderer {
                                    modReviewEntry {
                                        attrs.entry = it
                                        attrs.setUser = { userStr ->
                                            userRef.current?.value = userStr
                                            history.push(location.pathname + urlExtension())
                                        }
                                        attrs.onDelete = { reason ->
                                            axiosDelete<DeleteReview, String>(
                                                "${Config.apibase}/review/single/${it?.map?.id}/${it?.creator?.id}",
                                                DeleteReview(reason)
                                            )
                                        }
                                        attrs.onSave = onSave@{ sentiment, text ->
                                            Axios.put(
                                                "${Config.apibase}/review/single/${it?.map?.id}/${it?.creator?.id}",
                                                PutReview(text, sentiment ?: return@onSave null),
                                                generateConfig<PutReview, ActionResponse>()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        ReviewReplyDetail::class -> {
                            val loadPage = { toLoad: Int, token: CancelTokenSource ->
                                Axios.get<RepliesResponse>(
                                    "${Config.apibase}/reply/latest/$toLoad" + urlExtension(),
                                    generateConfig<String, RepliesResponse>(token.token)
                                ).then {
                                    return@then it.data.docs
                                }
                            }

                            child(RepliesInfiniteScroll::class) {
                                attrs.resultsKey = user
                                attrs.rowHeight = 95.5
                                attrs.itemsPerPage = 20
                                attrs.container = resultsTable
                                attrs.loadPage = loadPage
                                attrs.childFilter = {
                                    !it.hasClass("hiddenRow")
                                }
                                attrs.renderElement = InfiniteScrollElementRenderer {
                                    modReviewEntry {
                                        attrs.entry = it
                                        attrs.setUser = { userStr ->
                                            userRef.current?.value = userStr
                                            history.push(location.pathname + urlExtension())
                                        }
                                        attrs.onDelete = { reason ->
                                            axiosDelete<DeleteReview, String>(
                                                "${Config.apibase}/reply/single/${it?.id}",
                                                DeleteReview(reason)
                                            )
                                        }
                                        attrs.onSave = { _, text ->
                                            Axios.put(
                                                "${Config.apibase}/reply/single/${it?.id}",
                                                ReplyRequest(text),
                                                generateConfig<ReplyRequest, ActionResponse>()
                                            )
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

class RepliesInfiniteScroll : InfiniteScroll<ReviewReplyDetail>()
