package io.beatmaps.index

import external.Axios
import external.CancelTokenSource
import external.generateConfig
import external.routeLink
import io.beatmaps.Config
import io.beatmaps.History
import io.beatmaps.api.GenericSearchResponse
import io.beatmaps.api.MapDetail
import io.beatmaps.api.SearchResponse
import io.beatmaps.api.UserDetail
import io.beatmaps.common.EnvironmentSet
import io.beatmaps.common.MapTagSet
import io.beatmaps.common.SearchOrder
import io.beatmaps.common.api.RankedFilter
import io.beatmaps.common.toQuery
import io.beatmaps.configContext
import io.beatmaps.shared.InfiniteScrollElementRenderer
import io.beatmaps.shared.generateInfiniteScrollComponent
import io.beatmaps.shared.search.CommonParams
import io.beatmaps.util.encodeURIComponent
import io.beatmaps.util.fcmemo
import io.beatmaps.util.hashRegex
import io.beatmaps.util.includeIfNotNull
import io.beatmaps.util.useAudio
import io.beatmaps.util.useDidUpdateEffect
import react.Props
import react.RefObject
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h4
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.p
import react.router.useNavigate
import react.use
import react.useEffect
import react.useMemo
import react.useRef
import react.useState
import web.cssom.ClassName
import web.html.HTMLElement
import web.window.window
import kotlin.js.Promise

external interface BeatmapTableProps : Props {
    var search: SearchParams?
    var user: Int?
    var curated: Boolean?
    var wip: Boolean?
    var updateScrollIndex: RefObject<(Int) -> Unit>?
    var visible: Boolean?
    var fallbackOrder: SearchOrder?
    var fallbackAsc: Boolean?
}

data class SearchParams(
    override val search: String,
    val automapper: Boolean?,
    override val minNps: Float?,
    override val maxNps: Float?,
    val chroma: Boolean?,
    override val sortOrder: SearchOrder,
    override val ascending: Boolean?,
    override val from: String?,
    override val to: String?,
    val noodle: Boolean?,
    val ranked: RankedFilter?,
    val curated: Boolean?,
    val verified: Boolean?,
    val followed: Boolean?,
    val fullSpread: Boolean?,
    val me: Boolean?,
    val cinema: Boolean?,
    val vivify: Boolean?,
    val tags: MapTagSet,
    val environments: EnvironmentSet
) : CommonParams() {
    override fun queryParams() = super.queryParams() + listOfNotNull(
        includeIfNotNull(automapper, "automapper"),
        includeIfNotNull(chroma, "chroma"),
        includeIfNotNull(noodle, "noodle"),
        includeIfNotNull(me, "me"),
        includeIfNotNull(cinema, "cinema"),
        includeIfNotNull(vivify, "vivify"),
        includeIfNotNull(ranked, "leaderboard"),
        includeIfNotNull(curated, "curated"),
        includeIfNotNull(verified, "verified"),
        includeIfNotNull(followed, "followed"),
        includeIfNotNull(fullSpread, "fullSpread"),
        (if (environments.isNotEmpty()) "environments=${environments.joinToString(",")}" else null),
        tags.toQuery().let { tagStr -> (if (tagStr.isNotEmpty()) "tags=$tagStr" else null) }
    )
}

val beatmapTable = fcmemo<BeatmapTableProps>("beatmapTable") { props ->
    val (user, setUser) = useState<UserDetail?>(null)
    val resetRef = useRef<() -> Unit>()
    val itemsPerRow = useRef { if (window.innerWidth < 992) 1 else 2 }
    val loadPageRef = useRef<(Int, CancelTokenSource) -> Promise<GenericSearchResponse<MapDetail>?>>()

    val resultsTable = useRef<HTMLElement>()
    val audioRef = useAudio()

    val history = History(useNavigate())
    val config = use(configContext)

    useDidUpdateEffect(props.user, props.wip, props.curated, props.search, props.fallbackOrder, props.fallbackAsc) {
        setUser(null)
        resetRef.current?.invoke()
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
            "${Config.apibase}/search/${if (config?.v2Search == true) "text" else "v1"}/$page?curator=${props.user}&automapper=true" +
                (props.fallbackOrder?.let { "&sortOrder=$it" } ?: "") +
                (props.fallbackAsc?.let { "&ascending=$it" } ?: "")
        } else if (props.user != null) {
            "${Config.apibase}/search/${if (config?.v2Search == true) "text" else "v1"}/$page?collaborator=${props.user}&automapper=true" +
                (props.fallbackOrder?.let { "&sortOrder=$it" } ?: "") +
                (props.fallbackAsc?.let { "&ascending=$it" } ?: "")
        } else {
            props.search?.let { search ->
                "${Config.apibase}/search/${if (config?.v2Search == true) "text" else "v1"}/$page?" + search.queryParams().joinToString("&")
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

    loadPageRef.current = { toLoad: Int, token: CancelTokenSource ->
        if (toLoad == 0) loadUserSuggestion(token)

        Axios.get<SearchResponse>(
            getUrl(toLoad),
            generateConfig<String, SearchResponse>(token.token)
        ).then {
            if (it.data.redirect != null) {
                history.replace("/maps/" + it.data.redirect)
                return@then null
            }

            return@then it.data
        }
    }

    val renderer = useMemo(props.wip) {
        InfiniteScrollElementRenderer<MapDetail> { it ->
            beatmapInfo {
                obj = it
                version = it?.let { if (props.wip == true) it.latestVersion() else it.publishedVersion() }
                audio = audioRef
            }
        }
    }

    if (props.visible != false) {
        user?.let {
            routeLink(it.profileLink(), className = "card border-dark user-suggestion-card") {
                div {
                    className = ClassName("card-body")
                    h4 {
                        className = ClassName("card-title")
                        +"Were you looking for:"
                    }
                    p {
                        className = ClassName("card-text")
                        img {
                            alt = "${it.name} avatar"
                            src = it.avatar
                            className = ClassName("rounded-circle")
                            width = 40.0
                            height = 40.0
                        }
                        +it.name
                    }
                }
            }
        }
        div {
            className = ClassName("search-results")
            ref = resultsTable
            key = "resultsTable"

            mapDetailInfiniteScroll {
                this.resetRef = resetRef
                rowHeight = 155.0
                this.itemsPerRow = itemsPerRow
                itemsPerPage = 20
                container = resultsTable
                renderElement = renderer
                updateScrollIndex = props.updateScrollIndex
                loadPage = loadPageRef
            }
        }
    }
}

val mapDetailInfiniteScroll = generateInfiniteScrollComponent(MapDetail::class)
