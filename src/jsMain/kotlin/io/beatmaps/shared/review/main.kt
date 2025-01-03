package io.beatmaps.shared.review

import external.Axios
import external.CancelTokenSource
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.History
import io.beatmaps.api.MapDetail
import io.beatmaps.api.ReviewDetail
import io.beatmaps.api.ReviewsResponse
import io.beatmaps.api.UserDetail
import io.beatmaps.captcha.ICaptchaHandler
import io.beatmaps.captcha.captcha
import io.beatmaps.globalContext
import io.beatmaps.index.modalContext
import io.beatmaps.shared.InfiniteScroll
import io.beatmaps.shared.InfiniteScrollElementRenderer
import io.beatmaps.util.useDidUpdateEffect
import org.w3c.dom.HTMLElement
import react.Props
import react.dom.div
import react.fc
import react.router.useNavigate
import react.useContext
import react.useRef
import react.useState

external interface ReviewTableProps : Props {
    var map: MapDetail?
    var mapUploaderId: Int?
    var userDetail: UserDetail?
    var collaborators: List<UserDetail>?
    var visible: Boolean?
}

val reviewTable = fc<ReviewTableProps> { props ->
    val (resultsKey, setResultsKey) = useState(Any())
    val (existingReview, setExistingReview) = useState(false)

    val resultsTable = useRef<HTMLElement>()

    val captchaRef = useRef<ICaptchaHandler>()

    captcha(captchaRef)

    useDidUpdateEffect(props.map) {
        setResultsKey(Any())
    }

    fun getUrl(page: Int) = if (props.map != null) {
        "${Config.apibase}/review/map/${props.map?.id}/$page"
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

    if (props.visible != false) {
        div("reviews") {
            ref = resultsTable
            key = "resultsTable"

            globalContext.Consumer { userData ->
                props.map?.let { map ->
                    val userIsCollaborator = props.collaborators?.any { singleCollaborator ->
                        singleCollaborator.id == userData?.userId
                    } ?: false
                    if (userData != null && !userData.suspended && userData.userId != props.mapUploaderId && !userIsCollaborator) {
                        newReview {
                            attrs.mapId = map.id
                            attrs.userId = userData.userId
                            attrs.existingReview = existingReview
                            attrs.captcha = captchaRef
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
                        attrs.obj = rv?.copy(creator = props.userDetail ?: rv.creator)
                        attrs.userId = props.userDetail?.id ?: rv?.creator?.id ?: -1
                        attrs.map = props.map ?: rv?.map
                        attrs.captcha = captchaRef
                        attrs.setExistingReview = { nv ->
                            setExistingReview(nv)
                        }
                    }
                }
                attrs.loadPage = loadPage
            }
        }
    }
}

class CommentsInfiniteScroll : InfiniteScroll<ReviewDetail>()
