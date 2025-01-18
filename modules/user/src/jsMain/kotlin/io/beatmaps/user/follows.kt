package io.beatmaps.user

import external.Axios
import external.CancelTokenSource
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.GenericSearchResponse
import io.beatmaps.api.UserDetail
import io.beatmaps.shared.InfiniteScrollElementRenderer
import io.beatmaps.shared.userCard
import io.beatmaps.user.list.userInfiniteScroll
import io.beatmaps.util.fcmemo
import io.beatmaps.util.useDidUpdateEffect
import react.dom.html.ReactHTML.div
import react.useMemo
import react.useRef
import web.html.HTMLElement
import kotlin.js.Promise

val followList = fcmemo<FollowListProps>("followList") { props ->
    val resetRef = useRef<() -> Unit>()
    val loadPageRef = useRef<(Int, CancelTokenSource) -> Promise<GenericSearchResponse<UserDetail>?>>()

    val resultRef = useRef<HTMLElement>()

    useDidUpdateEffect(props.following, props.followedBy) {
        resetRef.current?.invoke()
    }

    fun getUrl(page: Int): String {
        return "${Config.apibase}/users" +
            when {
                props.following != null -> "/following/${props.following}/$page"
                props.followedBy != null -> "/followedBy/${props.followedBy}/$page"
                else -> { /* shouldn't happen */ }
            }
    }

    loadPageRef.current = { toLoad: Int, token: CancelTokenSource ->
        Axios.get<List<UserDetail>>(
            getUrl(toLoad),
            generateConfig<String, List<UserDetail>>(token.token)
        ).then {
            return@then GenericSearchResponse.from(it.data)
        }
    }

    val renderer = useMemo {
        InfiniteScrollElementRenderer<UserDetail> { u ->
            if (u != null) {
                userCard {
                    user = u
                }
            }
        }
    }

    div {
        ref = resultRef

        userInfiniteScroll {
            this.resetRef = resetRef
            rowHeight = 73.5
            itemsPerPage = 20
            container = resultRef
            scrollParent = props.scrollParent
            headerSize = 0.0
            loadPage = loadPageRef
            renderElement = renderer
        }
    }
}
