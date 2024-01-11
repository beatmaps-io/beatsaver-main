package io.beatmaps.shared.review

import external.Axios
import external.CancelTokenSource
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.ReviewDetail
import io.beatmaps.api.ReviewsResponse
import io.beatmaps.api.UserDetail
import io.beatmaps.globalContext
import io.beatmaps.index.modalContext
import io.beatmaps.shared.InfiniteScroll
import io.beatmaps.shared.InfiniteScrollElementRenderer
import io.beatmaps.util.useDidUpdateEffect
import org.w3c.dom.HTMLElement
import react.Props
import react.dom.div
import react.dom.p
import react.fc
import react.useContext
import react.useRef
import react.useState

external interface ReviewTableProps : Props {
    var map: String?
    var uploader: UserDetail?
    var userDetail: UserDetail?
    var fullWidth: Boolean?
}

val reviewTable = fc<ReviewTableProps> { props ->
    val (resultsKey, setResultsKey) = useState(Any())
    val (existingReview, setExistingReview) = useState(false)

    val resultsTable = useRef<HTMLElement>()
    val modal = useContext(modalContext)

    useDidUpdateEffect(props.map) {
        setResultsKey(Any())
    }

    fun getUrl(page: Int) = if (props.map != null) {
        "${Config.apibase}/review/map/${props.map}/$page"
    } else {
        props.userDetail?.id?.let { "${Config.apibase}/review/user/$it/$page" } ?: throw IllegalStateException()
    }

    val loadPage = { toLoad: Int, token: CancelTokenSource ->
        Axios.get<ReviewsResponse>(
            getUrl(toLoad),
            generateConfig<String, ReviewsResponse>(token.token)
        ).then {
            return@then it.data.docs
        }
    }

    if (props.uploader?.reviewsEnabled == false) {
        div(if (props.fullWidth == true) "" else " col-lg-8") {
            p("text-danger-light text-center p-3") {
                +"The uploader of this map has opted out of receiving reviews."
            }
        }
        return@fc
    }

    div("reviews" + if (props.fullWidth == true) "" else " col-lg-8") {
        ref = resultsTable
        key = "resultsTable"

        globalContext.Consumer { userData ->
            props.map?.let { map ->
                if (userData != null && !userData.suspended && userData.userId != props.uploader?.id) {
                    newReview {
                        attrs.mapId = map
                        attrs.userId = userData.userId
                        attrs.existingReview = existingReview
                        attrs.setExistingReview = { nv ->
                            setExistingReview(nv)
                        }
                        attrs.reloadList = {
                            setResultsKey(Any())
                        }
                    }
                }
            }
        }

        child(CommentsInfiniteScroll::class) {
            attrs.resultsKey = resultsKey
            attrs.rowHeight = 116.0
            attrs.itemsPerPage = 20
            attrs.container = resultsTable
            attrs.renderElement = InfiniteScrollElementRenderer { rv ->
                reviewItem {
                    obj = rv?.copy(creator = props.userDetail ?: rv.creator)
                    userId = props.userDetail?.id ?: rv?.creator?.id ?: -1
                    mapId = props.map ?: rv?.map?.id ?: ""
                    this.modal = modal
                    this.setExistingReview = { nv ->
                        setExistingReview(nv)
                    }
                }
            }
            attrs.loadPage = loadPage
        }
    }
}

class CommentsInfiniteScroll : InfiniteScroll<ReviewDetail>()
