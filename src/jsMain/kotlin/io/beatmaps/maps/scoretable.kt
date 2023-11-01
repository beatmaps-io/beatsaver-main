package io.beatmaps.maps

import external.Axios
import external.CancelTokenSource
import external.generateConfig
import external.invoke
import io.beatmaps.Config
import io.beatmaps.api.LeaderboardData
import io.beatmaps.api.LeaderboardScore
import io.beatmaps.api.LeaderboardType
import io.beatmaps.api.MapDifficulty
import io.beatmaps.common.fixedStr
import kotlinx.browser.window
import kotlinx.html.ThScope
import kotlinx.html.js.onScrollFunction
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import react.Props
import react.dom.a
import react.dom.div
import react.dom.img
import react.dom.table
import react.dom.tbody
import react.dom.th
import react.dom.thead
import react.dom.tr
import react.fc
import react.key
import react.useEffect
import react.useEffectOnce
import react.useRef
import react.useState

external interface ScoreTableProps : Props {
    var mapKey: String
    var selected: MapDifficulty?
    var type: LeaderboardType
}

val scoreTable = fc<ScoreTableProps> { props ->
    val (uid, setUid) = useState<String?>(null)
    val (scroll, setScroll) = useState(0.0)
    val (loading, setLoading) = useState(false)
    val (page, setPage) = useState(1)
    val (token, setToken) = useState<CancelTokenSource?>(null)
    val (scores, setScores) = useState(listOf<LeaderboardScore>())

    val myRef = useRef<HTMLElement>()

    val loadNextPage = {
        if (!loading) {
            setLoading(true)

            Axios.get<LeaderboardData>(
                "${Config.apibase}/scores/${props.mapKey}/$page?difficulty=${props.selected?.difficulty?.idx ?: 9}" +
                    "&gameMode=${props.selected?.characteristic?.ordinal ?: 0}&type=${props.type}",
                generateConfig<String, LeaderboardData>(token?.token)
            ).then {
                val newScores = it.data

                if (newScores.scores.isNotEmpty()) {
                    setScores(scores.plus(newScores.scores))
                    setLoading(false)
                    setPage(page + 1)
                    setUid(newScores.uid)

                    myRef.current?.scrollTop = scroll
                } else if (newScores.valid) {
                    setUid(newScores.uid)
                    setLoading(false)
                }
            }.catch {
                // Cancelled request
            }
        }
    }

    fun scoreColor(score: Int, maxScore: Int) =
        (score / maxScore.toFloat()).let {
            when {
                it > 0.9 -> "text-info"
                it > 0.8 -> "text-success"
                it > 0.7 -> ""
                it > 0.6 -> "text-warning"
                else -> "text-danger"
            }
        }

    val handleScroll = { _: Event ->
        val trigger = 100
        if (myRef.current != null) {
            val clientHeight = myRef.current?.clientHeight ?: 0
            val scrollTop = myRef.current?.scrollTop ?: 0.0
            val scrollHeight = myRef.current?.scrollHeight ?: 0

            setScroll(scrollTop)

            if (scrollHeight - (scrollTop + clientHeight) < trigger) {
                loadNextPage()
            }
        }
    }

    useEffectOnce {
        window.addEventListener("scroll", handleScroll)
        cleanup {
            window.removeEventListener("scroll", handleScroll)
        }
    }

    useEffect(token, page) {
        // Always load at least 2 pages
        if (token != null && page < 3) loadNextPage()
    }

    useEffect(props.selected, props.type) {
        token?.cancel("Another request started")

        setScores(listOf())
        setLoading(false)
        setScroll(0.0)
        setUid(null)
        setPage(1)
        setToken(Axios.CancelToken.source())
    }

    div("scores col-lg-8") {
        table("table table-striped table-dark") {
            thead {
                tr {
                    th(scope = ThScope.col) { +"#" }
                    th(scope = ThScope.col) { +"Player" }
                    th(scope = ThScope.col) { +"Score" }
                    th(scope = ThScope.col) { +"Mods" }
                    th(scope = ThScope.col) { +"%" }
                    th(scope = ThScope.col) { +"PP" }
                    th(scope = ThScope.col) {
                        uid?.let { uid1 ->
                            a("${props.type.url}$uid1", "_blank") {
                                img(props.type.name, src = "/static/${props.type.name.lowercase()}.svg") { }
                            }
                        }
                    }
                }
            }
            tbody {
                ref = myRef
                attrs.onScrollFunction = handleScroll
                scores.forEachIndexed { idx, it ->
                    val maxScore = props.selected?.maxScore ?: 0
                    score {
                        attrs.key = idx.toString()
                        attrs.position = idx + 1
                        attrs.playerId = it.playerId
                        attrs.name = it.name
                        attrs.pp = it.pp
                        attrs.score = it.score
                        attrs.scoreColor = scoreColor(it.score, maxScore)
                        attrs.mods = it.mods
                        attrs.percentage = ((it.score * 100L) / maxScore.toFloat()).fixedStr(2) + "%"
                    }
                }
            }
        }
    }
}
