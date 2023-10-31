package io.beatmaps.shared.review

import external.Axios
import external.CancelTokenSource
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.ReviewDetail
import io.beatmaps.api.ReviewsResponse
import io.beatmaps.api.UserDetail
import io.beatmaps.globalContext
import io.beatmaps.index.ModalComponent
import io.beatmaps.shared.InfiniteScroll
import io.beatmaps.shared.InfiniteScrollElementRenderer
import org.w3c.dom.HTMLElement
import react.Props
import react.RBuilder
import react.RComponent
import react.RefObject
import react.State
import react.createRef
import react.dom.div
import react.setState

external interface ReviewTableProps : Props {
    var map: String?
    var mapUploaderId: Int?
    var userDetail: UserDetail?
    var fullWidth: Boolean?
    var modal: RefObject<ModalComponent>
}

external interface ReviewTableState : State {
    var resultsKey: Any
    var existingReview: Boolean?
}

class ReviewTable : RComponent<ReviewTableProps, ReviewTableState>() {
    private val resultsTable = createRef<HTMLElement>()

    override fun componentWillUpdate(nextProps: ReviewTableProps, nextState: ReviewTableState) {
        if (props.map != nextProps.map) {
            nextState.apply {
                resultsKey = Any()
            }
        }
    }

    private fun getUrl(page: Int) = if (props.map != null) {
        "${Config.apibase}/review/map/${props.map}/$page"
    } else {
        props.userDetail?.id?.let { "${Config.apibase}/review/user/$it/$page" } ?: throw IllegalStateException()
    }

    private val loadPage = { toLoad: Int, token: CancelTokenSource ->
        Axios.get<ReviewsResponse>(
            getUrl(toLoad),
            generateConfig<String, ReviewsResponse>(token.token)
        ).then {
            return@then it.data.docs
        }
    }

    override fun RBuilder.render() {
        div("reviews" + if (props.fullWidth == true) "" else " col-lg-8") {
            ref = resultsTable
            key = "resultsTable"

            globalContext.Consumer { userData ->
                props.map?.let { map ->
                    if (userData != null && !userData.suspended && userData.userId != props.mapUploaderId) {
                        newReview {
                            attrs.mapId = map
                            attrs.userId = userData.userId
                            attrs.existingReview = state.existingReview
                            attrs.setExistingReview = { nv ->
                                setState {
                                    existingReview = nv
                                }
                            }
                            attrs.reloadList = {
                                setState {
                                    resultsKey = Any()
                                }
                            }
                        }
                    }
                }
            }

            child(CommentsInfiniteScroll::class) {
                attrs.resultsKey = state.resultsKey
                attrs.rowHeight = 116.0
                attrs.itemsPerPage = 20
                attrs.container = resultsTable
                attrs.renderElement = InfiniteScrollElementRenderer { rv ->
                    reviewItem {
                        obj = rv?.copy(creator = props.userDetail ?: rv.creator)
                        userId = props.userDetail?.id ?: rv?.creator?.id ?: -1
                        mapId = props.map ?: rv?.map?.id ?: ""
                        modal = props.modal
                        setExistingReview = { nv ->
                            setState {
                                existingReview = nv
                            }
                        }
                    }
                }
                attrs.loadPage = loadPage
            }
        }
    }
}

class CommentsInfiniteScroll : InfiniteScroll<ReviewDetail>()

fun RBuilder.reviewTable(handler: ReviewTableProps.() -> Unit) =
    child(ReviewTable::class) {
        this.attrs(handler)
    }
