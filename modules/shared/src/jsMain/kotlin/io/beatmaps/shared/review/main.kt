package io.beatmaps.shared.review

import external.CancelTokenSource
import external.axiosGet
import io.beatmaps.Config
import io.beatmaps.api.GenericSearchResponse
import io.beatmaps.api.MapDetail
import io.beatmaps.api.ReviewDetail
import io.beatmaps.api.ReviewsResponse
import io.beatmaps.api.UserDetail
import io.beatmaps.captcha.ICaptchaHandler
import io.beatmaps.captcha.captcha
import io.beatmaps.globalContext
import io.beatmaps.shared.InfiniteScrollElementRenderer
import io.beatmaps.shared.generateInfiniteScrollComponent
import io.beatmaps.util.fcmemo
import io.beatmaps.util.useDidUpdateEffect
import org.w3c.dom.HTMLElement
import react.Props
import react.dom.div
import react.useCallback
import react.useContext
import react.useMemo
import react.useRef
import react.useState
import kotlin.js.Promise

external interface ReviewTableProps : Props {
    var map: MapDetail?
    var mapUploaderId: Int?
    var userDetail: UserDetail?
    var collaborators: List<UserDetail>?
    var visible: Boolean?
}

val reviewTable = fcmemo<ReviewTableProps>("reviewTable") { props ->
    val resetRef = useRef<() -> Unit>()
    val loadPageRef = useRef<(Int, CancelTokenSource) -> Promise<GenericSearchResponse<ReviewDetail>?>>()
    val (existingReview, setExistingReview) = useState(false)

    val resultsTable = useRef<HTMLElement>()
    val userData = useContext(globalContext)

    val captchaRef = useRef<ICaptchaHandler>()

    useDidUpdateEffect(props.map) {
        resetRef.current?.invoke()
    }

    fun getUrl(page: Int) = if (props.map != null) {
        "${Config.apibase}/review/map/${props.map?.id}/$page"
    } else {
        props.userDetail?.id?.let { "${Config.apibase}/review/user/$it/$page" } ?: throw IllegalStateException()
    }

    loadPageRef.current = { toLoad: Int, token: CancelTokenSource ->
        axiosGet<ReviewsResponse>(getUrl(toLoad), token.token).then {
            return@then GenericSearchResponse.from(
                it.data.docs.map { rv ->
                    rv.copy(creator = props.userDetail ?: rv.creator)
                }
            )
        }
    }

    val updateReview = useCallback { nv: Boolean ->
        setExistingReview(nv)
    }

    val renderer = useMemo(props.map, props.userDetail) {
        InfiniteScrollElementRenderer<ReviewDetail> { rv ->
            reviewItem {
                attrs.obj = rv
                attrs.userId = props.userDetail?.id ?: rv?.creator?.id ?: -1
                attrs.map = props.map ?: rv?.map
                attrs.captcha = captchaRef
                attrs.setExistingReview = updateReview
            }
        }
    }

    captcha {
        key = "captcha"
        attrs.captchaRef = captchaRef
        attrs.page = "review"
    }

    if (props.visible != false) {
        div("reviews") {
            ref = resultsTable
            key = "resultsTable"

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
                            resetRef.current?.invoke()
                        }
                    }
                }
            }

            commentsInfiniteScroll {
                attrs.resetRef = resetRef
                attrs.rowHeight = 116.0
                attrs.itemsPerPage = 20
                attrs.container = resultsTable
                attrs.renderElement = renderer
                attrs.loadPage = loadPageRef
            }
        }
    }
}

val commentsInfiniteScroll = generateInfiniteScrollComponent(ReviewDetail::class)
