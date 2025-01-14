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
import io.beatmaps.util.useDidUpdateEffect
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import react.Props
import react.dom.div
import react.fc
import react.useMemo
import react.useRef
import kotlin.js.Promise

external interface FollowListProps : Props {
    var scrollParent: HTMLDivElement?
    var following: Int?
    var followedBy: Int?
}

val followList = fc<FollowListProps>("followList") { props ->
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
                    attrs.id = u.id
                    attrs.avatar = u.avatar
                    attrs.username = u.name
                    attrs.titles = userTitles(u)
                }
            }
        }
    }

    div {
        ref = resultRef

        userInfiniteScroll {
            attrs.resetRef = resetRef
            attrs.rowHeight = 73.5
            attrs.itemsPerPage = 20
            attrs.container = resultRef
            attrs.scrollParent = props.scrollParent
            attrs.headerSize = 0.0
            attrs.loadPage = loadPageRef
            attrs.renderElement = renderer
        }
    }
}
