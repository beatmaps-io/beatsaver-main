package io.beatmaps.user

import external.Axios
import external.CancelTokenSource
import external.generateConfig
import io.beatmaps.api.UserDetail
import io.beatmaps.common.Config
import io.beatmaps.shared.InfiniteScroll
import io.beatmaps.shared.InfiniteScrollElementRenderer
import io.beatmaps.shared.userCard
import io.beatmaps.util.userTitles
import org.w3c.dom.HTMLDivElement
import react.Props
import react.RBuilder
import react.RComponent
import react.State
import react.createRef
import react.dom.div
import react.setState

external interface FollowListProps : Props {
    var scrollParent: HTMLDivElement?
    var following: Int?
    var followedBy: Int?
}

external interface FollowListState : State {
    var resultsKey: Any
}

class FollowList : RComponent<FollowListProps, FollowListState>() {
    private val resultRef = createRef<HTMLDivElement>()

    override fun componentWillUpdate(nextProps: FollowListProps, nextState: FollowListState) {
        if (props.following != nextProps.following || props.followedBy != nextProps.followedBy) {
            setState {
                resultsKey = Any()
            }
        }
    }

    private fun getUrl(page: Int): String {
        return "${Config.apibase}/users" +
            when {
                props.following != null -> "/following/${props.following}/$page"
                props.followedBy != null -> "/followedBy/${props.followedBy}/$page"
                else -> { /* shouldn't happen */ }
            }
    }

    private val loadPage = { toLoad: Int, token: CancelTokenSource ->
        Axios.get<Array<UserDetail>>(
            getUrl(toLoad),
            generateConfig<String, Array<UserDetail>>(token.token)
        ).then {
            return@then it.data.toList()
        }
    }

    override fun RBuilder.render() {
        div {
            ref = resultRef

            child(FollowerInfiniteScroll::class) {
                attrs.resultsKey = state.resultsKey
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
}

class FollowerInfiniteScroll : InfiniteScroll<UserDetail>()

fun RBuilder.followList(handler: FollowListProps.() -> Unit) =
    child(FollowList::class) {
        this.attrs(handler)
    }
