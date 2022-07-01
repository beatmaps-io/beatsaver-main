package io.beatmaps.index

import io.beatmaps.api.SearchOrder
import io.beatmaps.common.MapTag
import io.beatmaps.common.MapTagType
import io.beatmaps.setPageTitle
import kotlinx.browser.window
import org.w3c.dom.url.URLSearchParams
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.createRef
import react.ref
import react.router.dom.RouteResultHistory
import react.setState

external interface HomePageProps : RProps {
    var history: RouteResultHistory
}
external interface HomePageState : RState {
    var searchParams: SearchParams
}

class HomePage : RComponent<HomePageProps, HomePageState>() {
    private val searchRef = createRef<Search>()
    private val modalRef = createRef<ModalComponent>()

    override fun componentWillMount() {
        setState {
            searchParams = fromURL()
        }
    }

    override fun componentDidMount() {
        setPageTitle("Home")

        searchRef.current?.updateUI(state.searchParams)
    }

    private fun fromURL() = URLSearchParams(window.location.search).let { params ->
        SearchParams(
            params.get("q") ?: "",
            params.get("auto")?.toBoolean(),
            params.get("minNps")?.toFloatOrNull(),
            params.get("maxNps")?.toFloatOrNull(),
            params.get("chroma")?.toBoolean(),
            SearchOrder.fromString(params.get("order")) ?: SearchOrder.Relevance,
            params.get("from"),
            params.get("to"),
            params.get("noodle")?.toBoolean(),
            params.get("ranked")?.toBoolean(),
            params.get("curated")?.toBoolean(),
            params.get("verified")?.toBoolean(),
            params.get("fullSpread")?.toBoolean(),
            params.get("me")?.toBoolean(),
            params.get("cinema")?.toBoolean(),
            params.get("tags")?.split(",", "|")?.groupBy { !it.startsWith("!") }?.mapValues {
                it.value.map { slug -> slug.removePrefix("!") }.groupBy { slug -> MapTag.fromSlug(slug)?.type ?: MapTagType.None }
            } ?: mapOf()
        )
    }

    override fun componentWillUpdate(nextProps: HomePageProps, nextState: HomePageState) {
        if (state.searchParams == nextState.searchParams) {
            val fromParams = fromURL()
            if (fromParams != state.searchParams) {
                searchRef.current?.updateUI(fromParams)
                nextState.searchParams = fromParams
            }
        }
    }

    private fun updateSearchParams(searchParamsLocal: SearchParams, row: Int?) {
        val tagStr = searchParamsLocal.tags.flatMap { x ->
            x.value.map { y ->
                y.value.joinToString(if (x.key) "|" else ",") {
                    (if (x.key) "" else "!") + it
                }
            }
        }.joinToString(",")

        val newQuery = listOfNotNull(
            (if (searchParamsLocal.search.isNotBlank()) "q=${searchParamsLocal.search}" else null),
            (if (searchParamsLocal.chroma == true) "chroma=true" else null),
            (if (searchParamsLocal.ranked == true) "ranked=true" else null),
            (if (searchParamsLocal.curated == true) "curated=true" else null),
            (if (searchParamsLocal.verified == true) "verified=true" else null),
            (if (searchParamsLocal.noodle == true) "noodle=true" else null),
            (if (searchParamsLocal.me == true) "me=true" else null),
            (if (searchParamsLocal.cinema == true) "cinema=true" else null),
            (if (searchParamsLocal.automapper == true) "auto=true" else null),
            (if (searchParamsLocal.fullSpread == true) "fullSpread=true" else null),
            (if (searchParamsLocal.maxNps != null) "maxNps=${searchParamsLocal.maxNps}" else null),
            (if (searchParamsLocal.minNps != null) "minNps=${searchParamsLocal.minNps}" else null),
            (if (searchParamsLocal.sortOrder != SearchOrder.Relevance) "order=${searchParamsLocal.sortOrder}" else null),
            (if (searchParamsLocal.from != null) "from=${searchParamsLocal.from}" else null),
            (if (searchParamsLocal.to != null) "to=${searchParamsLocal.to}" else null),
            (if (tagStr.isNotEmpty()) "tags=$tagStr" else null)
        )
        val hash = row?.let { "#$it" } ?: ""
        props.history.push((if (newQuery.isEmpty()) "/" else "?" + newQuery.joinToString("&")) + hash)

        setState {
            searchParams = searchParamsLocal
        }
    }

    override fun RBuilder.render() {
        search {
            ref = searchRef
            maxNps = 16
            updateSearchParams = {
                updateSearchParams(it, null)
            }
        }
        modal {
            ref = modalRef
        }
        beatmapTable {
            search = state.searchParams
            modal = modalRef
            history = props.history
            updateScrollIndex = {
                updateSearchParams(state.searchParams, if (it < 2) null else it)
            }
        }
    }
}
