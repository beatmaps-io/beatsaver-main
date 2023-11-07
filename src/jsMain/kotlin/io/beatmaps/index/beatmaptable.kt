package io.beatmaps.index

import external.Axios
import external.CancelTokenSource
import external.generateConfig
import external.routeLink
import io.beatmaps.Config
import io.beatmaps.History
import io.beatmaps.api.MapDetail
import io.beatmaps.api.SearchResponse
import io.beatmaps.api.UserDetail
import io.beatmaps.common.MapTagSet
import io.beatmaps.common.SearchOrder
import io.beatmaps.common.toQuery
import io.beatmaps.shared.InfiniteScroll
import io.beatmaps.shared.InfiniteScrollElementRenderer
import io.beatmaps.shared.search.CommonParams
import io.beatmaps.util.encodeURIComponent
import io.beatmaps.util.hashRegex
import io.beatmaps.util.useAudio
import io.beatmaps.util.useDidUpdateEffect
import io.beatmaps.util.useObjectMemoize
import kotlinx.browser.window
import kotlinx.datetime.Instant
import org.w3c.dom.HTMLElement
import react.Props
import react.dom.div
import react.dom.h4
import react.dom.img
import react.dom.p
import react.fc
import react.router.useNavigate
import react.useEffect
import react.useRef
import react.useState

external interface BeatmapTableProps : Props {
    var search: SearchParams?
    var user: Int?
    var curated: Boolean?
    var wip: Boolean?
    var updateScrollIndex: ((Int) -> Unit)?
    var visible: Boolean?
}

data class SearchParams(
    override val search: String,
    val automapper: Boolean?,
    override val minNps: Float?,
    override val maxNps: Float?,
    val chroma: Boolean?,
    override val sortOrder: SearchOrder,
    override val from: String?,
    override val to: String?,
    val noodle: Boolean?,
    val ranked: Boolean?,
    val curated: Boolean?,
    val verified: Boolean?,
    val fullSpread: Boolean?,
    val me: Boolean?,
    val cinema: Boolean?,
    val tags: MapTagSet
) : CommonParams()

val beatmapTable = fc<BeatmapTableProps> { props ->
    val (user, setUser) = useState<UserDetail?>(null)
    val (resultsKey, setResultsKey) = useState(Any())
    val minTimeRef = useRef<Instant>(null)

    val resultsTable = useRef<HTMLElement>()
    val audio = useAudio()

    val history = History(useNavigate())

    useDidUpdateEffect(props.user, props.wip, props.curated, props.search) {
        setUser(null)
        setResultsKey(Any())
    }

    useEffect(props.search) {
        props.search?.let { search ->
            if (hashRegex.matches(search.search)) {
                Axios.get<MapDetail>(
                    "${Config.apibase}/maps/hash/${encodeURIComponent(search.search)}",
                    generateConfig<String, MapDetail>()
                ).then {
                    history.replace("/maps/" + it.data.id)
                }.catch {
                    // Ignore errors, this is only a secondary request
                }
            }
        }
    }

    fun getUrl(page: Int) =
        if (props.wip == true) {
            "${Config.apibase}/maps/wip/$page"
        } else if (props.curated == true && props.user != null) {
            "${Config.apibase}/search/text/$page?sortOrder=Curated&curator=${props.user}&automapper=true"
        } else if (props.user != null) {
            "${Config.apibase}/maps/collaborations/${props.user}?" +
                (minTimeRef.current?.let { "&before=$it" } ?: "")
        } else {
            props.search?.let { search ->
                val tagStr = search.tags.toQuery()

                "${Config.apibase}/search/text/$page?sortOrder=${search.sortOrder}" +
                    (if (search.automapper != null) "&automapper=${search.automapper}" else "") +
                    (if (search.chroma != null) "&chroma=${search.chroma}" else "") +
                    (if (search.noodle != null) "&noodle=${search.noodle}" else "") +
                    (if (search.me != null) "&me=${search.me}" else "") +
                    (if (search.cinema != null) "&cinema=${search.cinema}" else "") +
                    (if (search.ranked != null) "&ranked=${search.ranked}" else "") +
                    (if (search.curated != null) "&curated=${search.curated}" else "") +
                    (if (search.verified != null) "&verified=${search.verified}" else "") +
                    (if (search.fullSpread != null) "&fullSpread=${search.fullSpread}" else "") +
                    (if (search.search.isNotBlank()) "&q=${encodeURIComponent(search.search)}" else "") +
                    (if (search.maxNps != null) "&maxNps=${search.maxNps}" else "") +
                    (if (search.minNps != null) "&minNps=${search.minNps}" else "") +
                    (if (search.from != null) "&from=${search.from}" else "") +
                    (if (search.to != null) "&to=${search.to}" else "") +
                    (if (tagStr.isNotEmpty()) "&tags=$tagStr" else "")
            } ?: ""
        }

    fun loadUserSuggestion(token: CancelTokenSource) {
        props.search?.let { search ->
            if (props.wip != true && props.curated != true && props.user == null && search.search.length > 2) {
                Axios.get<UserDetail>(
                    "${Config.apibase}/users/name/${encodeURIComponent(search.search)}",
                    generateConfig<String, UserDetail>(token.token)
                ).then {
                    setUser(it.data)
                }.catch {
                    // Ignore errors, this is only a secondary request
                }
            }
        }
    }

    val loadPage = { toLoad: Int, token: CancelTokenSource ->
        if (toLoad == 0) loadUserSuggestion(token)

        Axios.get<SearchResponse>(
            getUrl(toLoad),
            generateConfig<String, SearchResponse>(token.token)
        ).then {
            if (it.data.redirect != null) {
                history.replace("/maps/" + it.data.redirect)
                return@then null
            }

            val newMin = it.data.docs?.mapNotNull { doc -> doc.uploaded }?.minOrNull()
            if (newMin != null && (minTimeRef.current?.let { minTime -> newMin < minTime } != false)) {
                minTimeRef.current = newMin
            }
            return@then it.data.docs
        }
    }

    if (props.visible != false) {
        user?.let {
            routeLink(it.profileLink(), className = "card border-dark user-suggestion-card") {
                div("card-body") {
                    h4("card-title") {
                        +"Were you looking for:"
                    }
                    p("card-text") {
                        img("${it.name} avatar", it.avatar, classes = "rounded-circle") {
                            attrs.width = "40"
                            attrs.height = "40"
                        }
                        +it.name
                    }
                }
            }
        }
        div("search-results") {
            ref = resultsTable
            key = "resultsTable"

            child(MapDetailInfiniteScroll::class) {
                attrs.resultsKey = resultsKey
                attrs.rowHeight = 155.0
                attrs.itemsPerRow = { if (window.innerWidth < 992) 1 else 2 }
                attrs.itemsPerPage = 20
                attrs.container = resultsTable
                attrs.renderElement = InfiniteScrollElementRenderer { it ->
                    beatmapInfo {
                        obj = it
                        version = it?.let { if (props.wip == true) it.latestVersion() else it.publishedVersion() }
                        this.audio = audio.current
                    }
                }
                attrs.updateScrollIndex = props.updateScrollIndex
                attrs.loadPage = loadPage
            }
        }
    }
}

class MapDetailInfiniteScroll : InfiniteScroll<MapDetail>()
