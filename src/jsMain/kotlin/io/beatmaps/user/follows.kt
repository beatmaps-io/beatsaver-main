package io.beatmaps.user

import external.Axios
import external.CancelTokenSource
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.UserDetail
import io.beatmaps.shared.InfiniteScroll
import io.beatmaps.shared.InfiniteScrollElementRenderer
import io.beatmaps.shared.userCard
import io.beatmaps.util.useDidUpdateEffect
import io.beatmaps.util.userTitles
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import react.Props
import react.dom.div
import react.fc
import react.useRef
import react.useState

external interface FollowListProps : Props {
    var scrollParent: HTMLDivElement?
    var following: Int?
    var followedBy: Int?
}

val followList = fc<FollowListProps> { props ->
    val (resultsKey, setResultsKey) = useState(Any())

    val resultRef = useRef<HTMLElement>()

    useDidUpdateEffect(props.following, props.followedBy) {
        setResultsKey(Any())
    }

    fun getUrl(page: Int): String {
        return "${Config.apibase}/users" +
            when {
                props.following != null -> "/following/${props.following}/$page"
                props.followedBy != null -> "/followedBy/${props.followedBy}/$page"
                else -> { /* shouldn't happen */ }
            }
    }

    val loadPage = { toLoad: Int, token: CancelTokenSource ->
        Axios.get<Array<UserDetail>>(
            getUrl(toLoad),
            generateConfig<String, Array<UserDetail>>(token.token)
        ).then {
            return@then it.data.toList()
        }
    }

    div {
        ref = resultRef

        child(FollowerInfiniteScroll::class) {
            attrs.resultsKey = resultsKey
            attrs.rowHeight = 73.5
            attrs.itemsPerPage = 20
            attrs.container = resultRef
            attrs.scrollParent = props.scrollParent
            attrs.headerSize = 0.0
            attrs.loadPage = loadPage
            attrs.renderElement = InfiniteScrollElementRenderer { u ->
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
    }
}

class FollowerInfiniteScroll : InfiniteScroll<UserDetail>()
